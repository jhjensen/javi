#!/usr/bin/env python3
"""Javi remote test runner — rsync, Docker, parse, report.

Runs javi's JUnit and/or GUI tests on rdesk inside Docker with Xvfb.
Returns structured JSON results for easy agent consumption.

Usage examples:
    # Run all tests (headless + GUI)
    python3 ai/test_runner.py --all

    # Run only GUI tests
    python3 ai/test_runner.py --gui

    # Run only headless JUnit tests
    python3 ai/test_runner.py --headless

    # Run a specific test class
    python3 ai/test_runner.py --test-class Vt100ECScreenGuiJUnitTest

    # Clean build + all tests
    python3 ai/test_runner.py --clean --all

    # Quick mode (reuse base image, skip docker build)
    python3 ai/test_runner.py --quick --gui

    # Skip sync (source already on rdesk)
    python3 ai/test_runner.py --no-sync --gui

    # Just sync source, don't run tests
    python3 ai/test_runner.py --sync-only

    # Coverage report
    python3 ai/test_runner.py --coverage
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field, asdict
from pathlib import Path


# --- Configuration -----------------------------------------------------------

RDESK_HOST = "rdesk"
RDESK_DIR = "/tmp/javi-guitest"
GUITEST_IMAGE = "javi-guitest"
GUITEST_BASE_IMAGE = "javi-guitest-base"
ALLTEST_IMAGE = "javi-alltest"
ENTRYPOINT_SCRIPT = "ai/docker-entrypoint.sh"

RSYNC_EXCLUDE = [
    "build/", ".gradle", ".git", "*.dmp2",
    "ai.output", "ai/*.out", "bin", "oldstuff", "tmp",
]


# --- Data classes ------------------------------------------------------------

@dataclass
class TestResult:
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    failures: list = field(default_factory=list)
    build_status: str = "UNKNOWN"
    gradle_exit: int = -1
    duration_secs: float = 0.0
    error: str = ""


@dataclass
class RunResult:
    success: bool = False
    phase: str = ""
    headless: TestResult = field(default_factory=TestResult)
    gui: TestResult = field(default_factory=TestResult)
    sync_secs: float = 0.0
    build_secs: float = 0.0
    test_secs: float = 0.0
    total_secs: float = 0.0
    error: str = ""
    output_file: str = ""
    coverage_pct: dict = field(default_factory=dict)


# --- Helpers -----------------------------------------------------------------

def run_local(cmd, *, capture=True, timeout=600, cwd=None):
    """Run a command locally, return (returncode, stdout, stderr)."""
    if isinstance(cmd, str):
        cmd = ["sh", "-c", cmd]
    try:
        r = subprocess.run(
            cmd, capture_output=capture, text=True,
            timeout=timeout, cwd=cwd,
        )
        return r.returncode, r.stdout, r.stderr
    except subprocess.TimeoutExpired:
        return -1, "", f"Command timed out after {timeout}s"


def ssh_cmd(command, *, timeout=600):
    """Run a command on rdesk via SSH."""
    return run_local(
        ["ssh", "-n", "-T", RDESK_HOST, command],
        timeout=timeout,
    )


def timed(func):
    """Decorator that times a function and returns (result, elapsed)."""
    def wrapper(*args, **kwargs):
        t0 = time.monotonic()
        result = func(*args, **kwargs)
        elapsed = time.monotonic() - t0
        return result, elapsed
    return wrapper


# --- Phases ------------------------------------------------------------------

@timed
def phase_sync(source_dir):
    """Rsync source to rdesk."""
    excludes = " ".join(f"--exclude='{e}'" for e in RSYNC_EXCLUDE)
    cmd = f"rsync -az --delete {excludes} {source_dir}/ {RDESK_HOST}:{RDESK_DIR}/"
    rc, out, err = run_local(cmd, timeout=120)
    if rc != 0:
        return f"rsync failed (rc={rc}): {err}"
    return None  # success


@timed
def phase_docker_build(dockerfile, image_name, *, build_args=None):
    """Build a Docker image on rdesk."""
    ba = ""
    if build_args:
        ba = " ".join(f"--build-arg {k}={v}" for k, v in build_args.items())
    cmd = (
        f"cd {RDESK_DIR} && "
        f"docker build {ba} -f {dockerfile} -t {image_name} ."
    )
    rc, out, err = ssh_cmd(cmd, timeout=300)
    if rc != 0:
        return f"docker build failed (rc={rc}): {err[-500:]}"
    return None


@timed
def phase_docker_run(image_name, gradle_tasks, *, quick=False, test_filter=None):
    """Run tests in Docker on rdesk, return (error_or_None)."""
    tasks_str = " ".join(gradle_tasks)
    if test_filter:
        tasks_str += f" --tests '{test_filter}'"

    if quick:
        # Quick mode: use base image with volume-mounted source
        cmd = (
            f"cd {RDESK_DIR} && "
            # Clean up stale locks and ensure writable dirs
            f"docker run --rm -v $(pwd):/w alpine "
            f"sh -c \"rm -rf /w/.git; find /w/.gradle -name '*.lock' -delete 2>/dev/null; "
            f"mkdir -p /w/.gradle /w/build; chown $(id -u):$(id -g) /w/.gradle /w/build\" && "
            # Run tests with source copied from bind-mount to image fs
            f"docker run --rm "
            f"-v $(pwd):/src "
            f"-v $(pwd)/build:/app/build "
            f"--entrypoint '' "
            f"{image_name} "
            f"sh -c \"cp -r /src/src /app/src && cp /src/build.gradle /app/ && "
            f"/usr/local/bin/docker-entrypoint.sh {tasks_str}\""
        )
    else:
        # Full mode: source is COPY'd into image
        cmd = (
            f"cd {RDESK_DIR} && "
            f"mkdir -p build && "
            f"docker run --rm "
            f"--user $(id -u):$(id -g) "
            f"-v $(pwd)/build:/app/build "
            f"{image_name}"
        )

    rc, out, err = ssh_cmd(cmd, timeout=900)
    # rc != 0 is OK — we parse results from output files
    return None


@timed
def phase_fetch_results(local_build_dir):
    """Fetch test results and output files from rdesk."""
    local_build_dir.mkdir(parents=True, exist_ok=True)

    cmds = [
        f"rsync -az {RDESK_HOST}:{RDESK_DIR}/build/guitest-output.txt {local_build_dir}/ 2>/dev/null || true",
        f"rsync -az {RDESK_HOST}:{RDESK_DIR}/build/guitest-summary.txt {local_build_dir}/ 2>/dev/null || true",
        f"rsync -az {RDESK_HOST}:{RDESK_DIR}/build/alltest-output.txt {local_build_dir}/ 2>/dev/null || true",
        f"rsync -az {RDESK_HOST}:{RDESK_DIR}/build/reports/ {local_build_dir}/reports-rdesk/ 2>/dev/null || true",
        f"rsync -az {RDESK_HOST}:{RDESK_DIR}/build/test-results/ {local_build_dir}/test-results-rdesk/ 2>/dev/null || true",
        f"rsync -az {RDESK_HOST}:{RDESK_DIR}/build/jacoco/ {local_build_dir}/jacoco/ 2>/dev/null || true",
    ]
    for c in cmds:
        run_local(c, timeout=60)
    return None


# --- Result parsing ----------------------------------------------------------

def parse_gradle_output(text):
    """Parse Gradle test output text into a TestResult."""
    r = TestResult()

    # Count PASSED/FAILED/SKIPPED lines
    r.passed = len(re.findall(r' PASSED$', text, re.MULTILINE))
    r.failed = len(re.findall(r' FAILED$', text, re.MULTILINE))
    r.skipped = len(re.findall(r' SKIPPED$', text, re.MULTILINE))

    # Collect failure details
    for m in re.finditer(r'^(.+) FAILED$', text, re.MULTILINE):
        r.failures.append(m.group(1).strip())

    # Build status
    if "BUILD SUCCESSFUL" in text:
        r.build_status = "SUCCESSFUL"
    elif "BUILD FAILED" in text:
        r.build_status = "FAILED"
    elif r.passed > 0 and r.failed == 0:
        # Tests ran and passed — infer success even if BUILD line was truncated
        r.build_status = "SUCCESSFUL"
    elif r.passed > 0:
        # Tests ran but some failed
        r.build_status = "FAILED"

    # Gradle exit code
    m = re.search(r'=== Gradle exit: (\d+)', text)
    if m:
        r.gradle_exit = int(m.group(1))

    # Duration from timestamps
    starts = re.findall(r'=== Started: (.+?) ===', text)
    ends = re.findall(r'=== Finished: (.+?) ===', text)
    if starts and ends:
        try:
            from datetime import datetime
            t0 = datetime.strptime(starts[0], "%Y-%m-%d %H:%M:%S %Z")
            t1 = datetime.strptime(ends[0], "%Y-%m-%d %H:%M:%S %Z")
            r.duration_secs = (t1 - t0).total_seconds()
        except (ValueError, IndexError):
            pass

    return r


def parse_output_file(filepath):
    """Parse an output file and return TestResult."""
    if not filepath.exists():
        r = TestResult()
        r.error = f"Output file not found: {filepath}"
        return r
    text = filepath.read_text(errors="replace")
    return parse_gradle_output(text)


def parse_coverage_report(build_dir):
    """Parse JaCoCo merged XML report for per-package coverage percentages."""
    xml_file = build_dir / "reports-rdesk" / "jacoco" / "merged" / "merged.xml"
    if not xml_file.exists():
        # Try alternate location
        xml_file = build_dir / "reports" / "jacoco" / "merged" / "merged.xml"
    if not xml_file.exists():
        return {}

    import xml.etree.ElementTree as ET
    try:
        tree = ET.parse(xml_file)
    except ET.ParseError:
        return {}

    coverage = {}
    root = tree.getroot()
    for pkg in root.findall('.//package'):
        name = pkg.get('name', '').replace('/', '.')
        for counter in pkg.findall('counter'):
            if counter.get('type') == 'LINE':
                missed = int(counter.get('missed', 0))
                covered = int(counter.get('covered', 0))
                total = missed + covered
                if total > 0:
                    coverage[name] = round(100 * covered / total)
    # Overall
    for counter in root.findall('counter'):
        if counter.get('type') == 'LINE':
            missed = int(counter.get('missed', 0))
            covered = int(counter.get('covered', 0))
            total = missed + covered
            if total > 0:
                coverage['overall'] = round(100 * covered / total)

    return coverage


# --- Main orchestration ------------------------------------------------------

def run_tests(args):
    """Main test orchestration."""
    source_dir = Path(args.source_dir).resolve()
    build_dir = source_dir / "build"
    result = RunResult()
    total_t0 = time.monotonic()

    # Determine Gradle tasks
    gradle_tasks = []
    if args.clean:
        gradle_tasks.append("clean")
    if args.headless or args.all:
        gradle_tasks.append("test")
    if args.gui or args.all:
        gradle_tasks.append("guiTest")
    if args.coverage:
        gradle_tasks.extend(["test", "guiTest", "pstestCoverage",
                             "intArrayTestCoverage", "mergedCoverageReport"])

    if not gradle_tasks and not args.sync_only:
        gradle_tasks = ["guiTest"]  # default

    # Test filter for --test-class
    test_filter = None
    if args.test_class:
        test_filter = f"*{args.test_class}*"

    # Determine which image/mode to use
    if args.quick:
        image = GUITEST_BASE_IMAGE
    elif args.coverage or args.all:
        image = ALLTEST_IMAGE
        dockerfile = "Dockerfile.alltest"
    elif args.gui:
        image = GUITEST_IMAGE
        dockerfile = "Dockerfile.guitest"
    else:
        image = GUITEST_IMAGE
        dockerfile = "Dockerfile.guitest"

    # Phase 1: Sync
    if not args.no_sync:
        result.phase = "sync"
        if args.verbose:
            print("--- Phase: sync ---", file=sys.stderr)
        err, result.sync_secs = phase_sync(str(source_dir))
        if err:
            result.error = err
            result.total_secs = time.monotonic() - total_t0
            return result

    if args.sync_only:
        result.success = True
        result.phase = "sync-complete"
        result.total_secs = time.monotonic() - total_t0
        return result

    # Phase 2: Docker build (skip in quick mode)
    if not args.quick:
        result.phase = "build"
        if args.verbose:
            print("--- Phase: docker build ---", file=sys.stderr)
        build_args = None
        if args.coverage or args.all:
            # Cache-bust for alltest
            rc, out, _ = ssh_cmd(
                f"cd {RDESK_DIR} && find src -type f -newer Dockerfile.alltest -print | wc -l",
                timeout=30,
            )
            build_args = {"SRC_HASH": out.strip() if rc == 0 else "0"}
        err, result.build_secs = phase_docker_build(dockerfile, image, build_args=build_args)
        if err:
            result.error = err
            result.total_secs = time.monotonic() - total_t0
            return result

    # Phase 3: Run tests
    result.phase = "test"
    if args.verbose:
        print(f"--- Phase: test ({' '.join(gradle_tasks)}) ---", file=sys.stderr)
    err, result.test_secs = phase_docker_run(
        image, gradle_tasks, quick=args.quick, test_filter=test_filter,
    )
    if err:
        result.error = err
        result.total_secs = time.monotonic() - total_t0
        return result

    # Phase 4: Fetch results
    result.phase = "fetch"
    if args.verbose:
        print("--- Phase: fetch ---", file=sys.stderr)
    phase_fetch_results(build_dir)

    # Phase 5: Parse results
    result.phase = "parse"

    # Determine which output file to parse
    output_file = build_dir / "guitest-output.txt"
    alltest_file = build_dir / "alltest-output.txt"

    if args.coverage or args.all:
        # alltest output may be used
        if alltest_file.exists():
            output_file = alltest_file

    if output_file.exists():
        result.output_file = str(output_file)
        tr = parse_output_file(output_file)

        # Split headless vs GUI results if both were run
        if args.all or args.coverage:
            # Combined run — report totals in gui (primary output)
            result.gui = tr
        elif args.headless:
            result.headless = tr
        else:
            result.gui = tr

        # Determine overall success
        result.success = (tr.failed == 0 and tr.build_status != "FAILED")
    else:
        result.error = "No output file found after test run"

    # Parse coverage if requested
    if args.coverage:
        result.coverage_pct = parse_coverage_report(build_dir)

    result.total_secs = time.monotonic() - total_t0
    result.phase = "done"
    return result


def build_parser():
    p = argparse.ArgumentParser(
        description="Javi remote test runner — rsync + Docker + result parsing",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )

    # Test selection
    g = p.add_mutually_exclusive_group()
    g.add_argument("--gui", action="store_true",
                   help="Run GUI tests only (guiTest)")
    g.add_argument("--headless", action="store_true",
                   help="Run headless JUnit tests only (test)")
    g.add_argument("--all", action="store_true",
                   help="Run all tests (test + guiTest)")
    g.add_argument("--coverage", action="store_true",
                   help="Full coverage run (test + guiTest + legacy + merged report)")
    g.add_argument("--sync-only", action="store_true",
                   help="Just sync source to rdesk, don't run tests")

    # Filtering
    p.add_argument("--test-class", metavar="CLASS",
                   help="Run only tests matching this class name pattern")
    p.add_argument("--clean", action="store_true",
                   help="Clean build before running tests")

    # Execution mode
    p.add_argument("--quick", action="store_true",
                   help="Quick mode: use base image, skip docker build")
    p.add_argument("--no-sync", action="store_true",
                   help="Skip rsync (source already on rdesk)")

    # Output
    p.add_argument("--json", action="store_true",
                   help="Output results as JSON (default: human-readable)")
    p.add_argument("--verbose", "-v", action="store_true",
                   help="Print progress to stderr")

    # Source directory
    p.add_argument("--source-dir", default=".",
                   help="Javi source directory (default: current dir)")

    return p


def format_human(result):
    """Format RunResult as human-readable text."""
    lines = []
    lines.append("=== Javi Test Results ===")

    if result.error:
        lines.append(f"ERROR: {result.error}")
        lines.append(f"Phase: {result.phase}")
        return "\n".join(lines)

    # Timing
    parts = []
    if result.sync_secs > 0:
        parts.append(f"sync={result.sync_secs:.1f}s")
    if result.build_secs > 0:
        parts.append(f"build={result.build_secs:.1f}s")
    if result.test_secs > 0:
        parts.append(f"test={result.test_secs:.1f}s")
    parts.append(f"total={result.total_secs:.1f}s")
    lines.append(f"Timing: {', '.join(parts)}")

    # Results
    for label, tr in [("Headless", result.headless), ("GUI", result.gui)]:
        if tr.passed or tr.failed or tr.skipped:
            status = "PASS" if tr.failed == 0 else "FAIL"
            lines.append(
                f"{label}: {tr.passed} passed, {tr.failed} failed, "
                f"{tr.skipped} skipped [{status}]"
            )
            if tr.failures:
                lines.append(f"  Failures:")
                for f in tr.failures:
                    lines.append(f"    - {f}")

    # Coverage
    if result.coverage_pct:
        lines.append("Coverage:")
        for pkg, pct in sorted(result.coverage_pct.items()):
            lines.append(f"  {pkg}: {pct}%")

    # Overall
    lines.append(f"Result: {'SUCCESS' if result.success else 'FAILURE'}")

    return "\n".join(lines)


def main():
    parser = build_parser()
    args = parser.parse_args()

    result = run_tests(args)

    if args.json:
        print(json.dumps(asdict(result), indent=2))
    else:
        print(format_human(result))

    sys.exit(0 if result.success else 1)


if __name__ == "__main__":
    main()

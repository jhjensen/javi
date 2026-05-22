#!/usr/bin/env python3
"""Javi remote test runner — rsync, Docker, parse, report.

Runs javi's JUnit and/or GUI tests on rdesk inside Docker with Xvfb.
Returns structured results (JSON or human-readable) for agent consumption.

ALL remote test operations should go through this tool.
Do NOT use SSH directly for anything this tool can do.
If you need a capability that doesn't exist, add it here.

== COMMANDS ==

  Run tests:
    python3 ai/test_runner.py --all               All tests (headless + GUI)
    python3 ai/test_runner.py --gui               GUI tests only
    python3 ai/test_runner.py --headless          Headless JUnit tests only
    python3 ai/test_runner.py --coverage          Full coverage run
    python3 ai/test_runner.py --clean --all       Clean build + all tests
    python3 ai/test_runner.py --quick --gui       Quick (reuse base image)
    python3 ai/test_runner.py --test-class Foo    Single test class

  Fetch / inspect:
    python3 ai/test_runner.py --fetch-logs        Download all test output from rdesk
    python3 ai/test_runner.py --sync-only         Sync source to rdesk only

  Cleanup:
    python3 ai/test_runner.py --clean-remote      Remove Docker images + files on rdesk
    python3 ai/test_runner.py --kill               Kill running Docker test containers

  Options:
    --json          JSON output (includes failure details: class, method, message)
    --no-sync       Skip rsync (source already on rdesk)
    -v / --verbose  Progress messages to stderr

== OUTPUT FILES (after any run or --fetch-logs) ==

  build/guitest-output.txt   — full Gradle output (GUI runs)
  build/alltest-output.txt   — full Gradle output (all-test / coverage runs)
  build/guitest-summary.txt  — parsed summary
  build/reports-rdesk/       — HTML test reports
  build/test-results-rdesk/  — JUnit XML results
  build/jacoco/              — JaCoCo coverage data

== JSON OUTPUT ==

  When --json is used, the output includes:
    success (bool), phase, error,
    headless/gui: {passed, failed, skipped, failures, build_status, gradle_exit},
    sync_secs, build_secs, test_secs, total_secs,
    coverage_pct (dict), output_file (local path to full Gradle log)

  The "failures" list contains dicts with keys:
    test  — full test name (e.g. "ClassName > methodName()")
    message — failure message / first lines of stack trace

  Read the local output_file for complete Gradle output including full stack traces.

== AGENT WORKFLOW ==

  1. Run --help at the start of every iteration to check capabilities.
  2. Use this tool for ALL remote operations — never SSH manually.
  3. After a run, read the local output file (build/*-output.txt) for details.
  4. If a needed capability is missing, add it to this script.
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
class FailureDetail:
    test: str = ""
    message: str = ""


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
        # Full mode: source is COPY'd into image.
        # Always clean stale build dir via root Docker container — previous
        # runs may leave root-owned files that --user can't overwrite.
        cmd = (
            f"cd {RDESK_DIR} && "
            f"docker run --rm -v $(pwd)/build:/b alpine "
            f"sh -c 'rm -rf /b/* /b/.* 2>/dev/null; true' && "
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
    """Fetch ALL test results and output files from rdesk."""
    local_build_dir.mkdir(parents=True, exist_ok=True)

    # Sync everything useful from the remote build directory.
    # Use --delete on subdirs to remove stale local results.
    cmds = [
        # Full Gradle output logs (most important — agents read these)
        f"rsync -az {RDESK_HOST}:{RDESK_DIR}/build/*.txt {local_build_dir}/ 2>/dev/null || true",
        # HTML test reports (delete stale local files)
        f"rsync -az --delete {RDESK_HOST}:{RDESK_DIR}/build/reports/ {local_build_dir}/reports-rdesk/ 2>/dev/null || true",
        # JUnit XML results (delete stale local files)
        f"rsync -az --delete {RDESK_HOST}:{RDESK_DIR}/build/test-results/ {local_build_dir}/test-results-rdesk/ 2>/dev/null || true",
        # JaCoCo coverage data
        f"rsync -az {RDESK_HOST}:{RDESK_DIR}/build/jacoco/ {local_build_dir}/jacoco/ 2>/dev/null || true",
    ]
    for c in cmds:
        run_local(c, timeout=60)
    return None


def do_clean_remote():
    """Remove Docker images, containers, and files on rdesk."""
    steps = [
        # Kill any running test containers
        (
            "docker ps -q --filter ancestor=javi-guitest "
            "--filter ancestor=javi-guitest-base "
            "--filter ancestor=javi-alltest "
            "| xargs -r docker kill 2>/dev/null; "
            "docker ps -q --filter ancestor=javi-guitest "
            "--filter ancestor=javi-guitest-base "
            "--filter ancestor=javi-alltest "
            "| xargs -r docker rm 2>/dev/null; true"
        ),
        # Remove images
        (
            "docker rmi javi-guitest javi-guitest-base javi-alltest "
            "2>/dev/null; true"
        ),
        # Remove remote files
        f"rm -rf {RDESK_DIR}",
    ]
    for cmd in steps:
        rc, out, err = ssh_cmd(cmd, timeout=60)
    print("Remote cleanup complete.", file=sys.stderr)


def do_kill():
    """Kill any running Docker test containers on rdesk."""
    cmd = (
        "docker ps --filter ancestor=javi-guitest "
        "--filter ancestor=javi-guitest-base "
        "--filter ancestor=javi-alltest "
        "--format '{{.ID}} {{.Image}} {{.Status}}'"
    )
    rc, out, err = ssh_cmd(cmd, timeout=30)
    if rc != 0:
        print(f"Failed to list containers: {err}", file=sys.stderr)
        return False

    if not out.strip():
        print("No running test containers found.", file=sys.stderr)
        return True

    print(f"Running containers:\n{out.strip()}", file=sys.stderr)
    kill_cmd = (
        "docker ps -q --filter ancestor=javi-guitest "
        "--filter ancestor=javi-guitest-base "
        "--filter ancestor=javi-alltest "
        "| xargs -r docker kill 2>/dev/null; true"
    )
    rc, out, err = ssh_cmd(kill_cmd, timeout=30)
    print("Containers killed.", file=sys.stderr)
    return True


def do_fetch_logs(source_dir):
    """Download all test output from rdesk without running tests."""
    build_dir = Path(source_dir).resolve() / "build"
    _, elapsed = phase_fetch_results(build_dir)
    print(f"Fetched logs to {build_dir}/ ({elapsed:.1f}s)", file=sys.stderr)

    # List what was downloaded
    for f in sorted(build_dir.glob("*.txt")):
        size = f.stat().st_size
        print(f"  {f.name} ({size:,} bytes)", file=sys.stderr)
    return True


# --- Result parsing ----------------------------------------------------------

def parse_gradle_output(text):
    """Parse Gradle test output text into a TestResult."""
    r = TestResult()

    # Count PASSED/FAILED/SKIPPED lines
    r.passed = len(re.findall(r' PASSED$', text, re.MULTILINE))
    r.failed = len(re.findall(r' FAILED$', text, re.MULTILINE))
    r.skipped = len(re.findall(r' SKIPPED$', text, re.MULTILINE))

    # Collect failure details with context (class, method, message).
    # Gradle output pattern:
    #   ClassName > methodName() FAILED
    #       org.opentest4j.AssertionFailedError: expected: ...
    #           at org.junit...
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if not line.rstrip().endswith(' FAILED'):
            continue
        test_name = line.rstrip()[:-len(' FAILED')].strip()
        # Gather indented lines after the FAILED line as the failure message.
        # Stop at the next test result line or non-indented line.
        msg_lines = []
        for j in range(i + 1, min(i + 30, len(lines))):
            subsequent = lines[j]
            # Stop if we hit another test result or blank-then-result
            if re.search(r' (PASSED|FAILED|SKIPPED|STARTED)$', subsequent):
                break
            if subsequent.strip() == '':
                # Blank line might separate — peek ahead
                if j + 1 < len(lines) and re.search(
                    r' (PASSED|FAILED|SKIPPED|STARTED)$', lines[j + 1]
                ):
                    break
                if subsequent.strip() == '' and msg_lines:
                    break
                continue
            msg_lines.append(subsequent.rstrip())
            # Cap message to first 5 meaningful lines
            if len(msg_lines) >= 5:
                break
        message = "\n".join(msg_lines).strip()
        r.failures.append(asdict(FailureDetail(test=test_name, message=message)))

    # Build status
    if "BUILD SUCCESSFUL" in text:
        r.build_status = "SUCCESSFUL"
    elif "BUILD FAILED" in text:
        r.build_status = "FAILED"
    elif r.passed > 0 and r.failed == 0:
        r.build_status = "SUCCESSFUL"
    elif r.passed > 0:
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
        prog="python3 ai/test_runner.py",
        description="Javi remote test runner — rsync + Docker + result parsing.\n\n"
            "This tool handles ALL remote test operations: syncing source,\n"
            "building Docker images, running tests, fetching results, and\n"
            "cleaning up. Agents should NEVER use SSH directly for anything\n"
            "this tool can do. If a capability is missing, add it here.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""\
EXAMPLES:
  %(prog)s --all --json           Run all tests, JSON output with failure details
  %(prog)s --clean --all --json   Clean build + all tests
  %(prog)s --quick --gui          Quick rebuild, GUI tests only
  %(prog)s --test-class FooTest   Run a single test class
  %(prog)s --fetch-logs           Download output files without re-running
  %(prog)s --clean-remote         Remove all Docker artifacts on rdesk
  %(prog)s --kill                 Kill running test containers on rdesk

AGENT WORKFLOW:
  1. Run '%(prog)s --help' at the start of every iteration.
  2. Use this tool for ALL remote operations — never SSH manually.
  3. After a run, read the local output file for complete Gradle log.
  4. JSON --failures[] include {test, message} for each failure.
  5. If a needed capability is missing, modify this script.

OUTPUT FILES (synced locally after every run or --fetch-logs):
  build/guitest-output.txt    Full Gradle output (GUI test runs)
  build/alltest-output.txt    Full Gradle output (--all / --coverage runs)
  build/guitest-summary.txt   Parsed test summary
  build/reports-rdesk/        HTML test reports
  build/test-results-rdesk/   JUnit XML results
  build/jacoco/               JaCoCo coverage data

JSON OUTPUT STRUCTURE:
  success: bool               Overall pass/fail
  phase: str                  Last completed phase
  error: str                  Error message if failed
  output_file: str            Local path to full Gradle log
  headless/gui:
    passed: int               Number of passed tests
    failed: int               Number of failed tests
    skipped: int              Number of skipped tests
    failures: [{test, message}]  Failed test details
    build_status: str         SUCCESSFUL / FAILED / UNKNOWN
    gradle_exit: int          Gradle process exit code
  sync_secs, build_secs, test_secs, total_secs: float
  coverage_pct: {pkg: int}    Per-package line coverage percent
""",
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
    g.add_argument("--fetch-logs", action="store_true",
                   help="Download all test output from rdesk without re-running tests")
    g.add_argument("--clean-remote", action="store_true",
                   help="Remove Docker images, containers, and files on rdesk")
    g.add_argument("--kill", action="store_true",
                   help="Kill any running Docker test containers on rdesk")

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
                lines.append("  Failures:")
                for f in tr.failures:
                    if isinstance(f, dict):
                        lines.append(f"    - {f.get('test', '?')}")
                        msg = f.get('message', '')
                        if msg:
                            for mline in msg.splitlines()[:3]:
                                lines.append(f"        {mline}")
                    else:
                        lines.append(f"    - {f}")

    # Coverage
    if result.coverage_pct:
        lines.append("Coverage:")
        for pkg, pct in sorted(result.coverage_pct.items()):
            lines.append(f"  {pkg}: {pct}%")

    # Output file location
    if result.output_file:
        lines.append(f"Full log: {result.output_file}")

    # Overall
    lines.append(f"Result: {'SUCCESS' if result.success else 'FAILURE'}")

    return "\n".join(lines)


def main():
    parser = build_parser()
    args = parser.parse_args()

    # Handle standalone commands that don't run tests
    if args.clean_remote:
        do_clean_remote()
        sys.exit(0)

    if args.kill:
        ok = do_kill()
        sys.exit(0 if ok else 1)

    if args.fetch_logs:
        ok = do_fetch_logs(args.source_dir)
        sys.exit(0 if ok else 1)

    result = run_tests(args)

    if args.json:
        print(json.dumps(asdict(result), indent=2))
    else:
        print(format_human(result))

    sys.exit(0 if result.success else 1)


if __name__ == "__main__":
    main()

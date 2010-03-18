package javt;
abstract class Result {
  abstract String getMatch(int index);
  abstract int getMatchStart(int index);
  abstract int getMatchEnd(int index);
  abstract int getMatchEnd();
  abstract int getMatchStart();
}

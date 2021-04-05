set -e
for i in {1..100}
do
  git reset --hard
  mvn formatter:format -T6 -Prelease,communityRelease -e -Dorg.slf4j.simpleLogger.log.net.revelc.code.formatter=debug
done

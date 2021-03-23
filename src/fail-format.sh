rm -rf /tmp/*
git reset --hard
git clean -fd
mvn formatter:format -nsu -T6 -Prelease,communityRelease

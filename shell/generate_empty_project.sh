# taken from https://github.com/openjdk/jmh
mvn archetype:generate \
  -DinteractiveMode=false \
  -DarchetypeGroupId=org.openjdk.jmh \
  -DarchetypeArtifactId=jmh-java-benchmark-archetype \
  -DgroupId=org.fedorov \
  -DartifactId=javatests \
  -Dversion=1.0

# kafka-streams-sandbox
Apache Kafka Streams (KStream and KTable)

docker-compose up -d (or docker-compose up -d -- broker : if schema-registry is not required)

Check properties for Kafka broker and topics in ./configuration/dev.properties

./gradlew clean build shadowJar
./gradlew run

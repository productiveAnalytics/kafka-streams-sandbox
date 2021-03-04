# kafka-streams-sandbox
Apache Kafka Streams (KStream and KTable).

docker-compose up -d (or docker-compose up -d -- broker : if schema-registry is not required)

Confirm the connectivity to kafka broker:
nc -vz localhost 29092

Check properties for Kafka broker and topics in ./configuration/dev.properties

./gradlew clean build shadowJar
./gradlew run 
(or to debug ./gradlew run --debug-jvm and start VSCode to listen on port 5005)

Check all topics:
cd <kafka-folder>
./bin/kafka-topics.sh --bootstrap-server localhost:29092 --list

PRODUCE message to topic:
cd <kafka-folder>
./bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic input-test-topic --property parse.key=true --property key.separator=":"
enter message(s) in format key1:value1<enter>

COMSUME from topic for the KTable:
cd <kafka-folder>
./bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 --topic table-output-test-topic --from-beginning --property print.key=true --property key.separator=" = " 

CLEAN-UP topics:
cd <kafka-folder>
for my_topic in $(./bin/kafka-topics.sh --bootstrap-server localhost:29092 --list); 
do 
    ./bin/kafka-topics.sh --bootstrap-server localhost:29092 --topic $my_topic --delete; 
    echo "Deleted topic: " $my_topic; 
done;

To visualize topology: https://zz85.github.io/kafka-streams-viz/
Topologies:
   Sub-topology: 0
    Source: KSTREAM-SOURCE-0000000000 (topics: [input-test-topic])
      --> KSTREAM-SINK-0000000002, my-ktable
    Processor: my-ktable (stores: [stream-converted-to-table])
      --> KTABLE-TOSTREAM-0000000003
      <-- KSTREAM-SOURCE-0000000000
    Processor: KTABLE-TOSTREAM-0000000003 (stores: [])
      --> KSTREAM-SINK-0000000004
      <-- my-ktable
    Sink: KSTREAM-SINK-0000000002 (topic: streams-output-test-topic)
      <-- KSTREAM-SOURCE-0000000000
    Sink: KSTREAM-SINK-0000000004 (topic: table-output-test-topic)
      <-- KTABLE-TOSTREAM-0000000003

References:
1. https://kafka-tutorials.confluent.io/kafka-streams-convert-to-ktable/kstreams.html
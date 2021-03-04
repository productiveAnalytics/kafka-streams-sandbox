package com.productiveanalytics.kafkastreams;

import static org.apache.kafka.streams.Topology.AutoOffsetReset.EARLIEST;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Materialized;

public class KStreamsToKTable {
    private static final String BOOTSTRAP_SERVERS = "bootstrap.servers";
    private static final Serde<String> STRING_SER_DE = Serdes.String();

	private static Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty(BOOTSTRAP_SERVERS));

        // default SerDe for key and valueß
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, STRING_SER_DE.getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, STRING_SER_DE.getClass());

        return props;
    }

    private static Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = envProps.getProperty("input.topic.name");
        final String streamsOutputTopic = envProps.getProperty("streams.output.topic.name");
        final String tableOutputTopic = envProps.getProperty("table.output.topic.name");
        final String filteredTableOutputTopic = envProps.getProperty("filtered.table.output.topic.name");

        // auto.offset.reset=earliest
        Consumed<String, String> consumerConf = Consumed.with(STRING_SER_DE, STRING_SER_DE).withOffsetResetPolicy(EARLIEST);
        final KStream<String, String> stream = builder.stream(inputTopic, consumerConf);

        final KTable<String, String> convertedTable = stream.toTable(
            Named.as("my-ktable"),
            Materialized.as("stream-converted-to-table")
        );
        // Dummy way to peek into table
        convertedTable.mapValues((k,v) -> System.err.printf("[KTABLE-WATCH] Key: %s, Value: %s %n", k, v));

        final KTable<String, String> filteredTable = convertedTable.filter(
            ((k,v) -> k.equalsIgnoreCase(v)),
            Named.as("my-filtered-ktable"),
            Materialized.as("filtered-table-where-key-eq-value")
        );

        Produced<String, String> producerConf = Produced.with(STRING_SER_DE, STRING_SER_DE);

        stream.to(streamsOutputTopic, producerConf);
        convertedTable.toStream().to(tableOutputTopic, producerConf);
        filteredTable.toStream().to(filteredTableOutputTopic, producerConf);

        return builder.build();
    }


    private static void createTopics(final Properties envProps) {
        final Map<String, Object> config = new HashMap<>();
        config.put(BOOTSTRAP_SERVERS, envProps.getProperty(BOOTSTRAP_SERVERS));
        try (final AdminClient client = AdminClient.create(config)) {

            final Integer partitions = Integer.parseInt(envProps.getProperty("topic.partitions"));
            final Short replicationFactor = Short.parseShort(envProps.getProperty("topic.replication.factor"));
            final List<NewTopic> topics = new ArrayList<>();

            topics.add(new NewTopic(
                envProps.getProperty("input.topic.name"),
                partitions,
                replicationFactor));

            topics.add(new NewTopic(
                envProps.getProperty("streams.output.topic.name"),
                partitions,
                replicationFactor));

            topics.add(new NewTopic(
                envProps.getProperty("table.output.topic.name"),
                partitions,
                replicationFactor));

            topics.add(new NewTopic(
                envProps.getProperty("filtered.table.output.topic.name"),
                partitions,
                replicationFactor));

            client.createTopics(topics);
        }
    }

    private static Properties loadEnvProperties(String fileName) throws IOException {
        final Properties envProps = new Properties();
        try (FileInputStream input = new FileInputStream(fileName)) {
            envProps.load(input);
        }

        return envProps;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        final KStreamsToKTable instance = new KStreamsToKTable();
        final Properties envProps = loadEnvProperties(args[0]);
        createTopics(envProps);
        System.out.println("[DEBUG] Topics created...");

        final Properties streamProps = buildStreamsProperties(envProps);
        final Topology topology = buildTopology(envProps);

        String topologyAsStr = topology.describe().toString();
        System.out.println("===========================");
        System.out.println(topologyAsStr);
        System.out.println("===========================");

        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close(Duration.ofSeconds(5));
                latch.countDown();
            }
        });

        try {
            streams.cleanUp();
            streams.start();
            latch.await();
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

}
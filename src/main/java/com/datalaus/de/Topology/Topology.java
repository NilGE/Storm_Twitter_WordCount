package com.datalaus.de.Topology;

import java.io.Serializable;
import java.util.Properties;
import java.util.UUID;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.utils.Utils;
import backtype.storm.spout.SchemeAsMultiScheme;
import storm.kafka.BrokerHosts;
import storm.kafka.KafkaSpout;
import storm.kafka.SpoutConfig;
import storm.kafka.StringScheme;
import storm.kafka.ZkHosts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datalaus.de.bolts.WordCounterBolt;
import com.datalaus.de.bolts.WordSplitterBolt;
import com.datalaus.de.bolts.TweetKafkabolt;
import com.datalaus.de.utils.Constants;

public class Topology implements Serializable {
	private static final Logger LOGGER = LoggerFactory.getLogger(Topology.class);
	static final String TOPOLOGY_NAME = "storm-twitter-word-count";
	
	public static final void main(final String[] args) {
		try {
			
			Properties topologyConfig = null;
			final Config config = new Config();
			config.setMessageTimeoutSecs(20);

			TopologyBuilder topologyBuilder = new TopologyBuilder();
			
			String configFileLocation = "./config.properties";
		    topologyConfig = new Properties();
		    topologyConfig.load(ClassLoader.getSystemResourceAsStream(configFileLocation));
		    String zkConnString = topologyConfig.getProperty("zookeeper");
		    String topicName = topologyConfig.getProperty("topic");
			BrokerHosts hosts = new ZkHosts(zkConnString);
			SpoutConfig spoutConfig = new SpoutConfig(hosts, topicName, "/" + topicName, UUID.randomUUID().toString());
			spoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
			KafkaSpout kafkaSpout = new KafkaSpout(spoutConfig);

		    // attach the tweet spout to the topology - parallelism of 1
			topologyBuilder.setSpout("kafka-tweet-spout", kafkaSpout, 1);
			
			
			//topologyBuilder.setBolt("DisplayBolt", new DisplayBolt()).shuffleGrouping("twitterspout");
			topologyBuilder.setBolt("tweet-original", new TweetKafkabolt(),1).shuffleGrouping("kafka-tweet-spout");
			topologyBuilder.setBolt("WordSplitterBolt", new WordSplitterBolt(5)).shuffleGrouping("tweet-original");
			//topologyBuilder.setBolt("WordCounterBolt", new WordCounterBolt(10, 5 * 60, 50)).shuffleGrouping("WordSplitterBolt");
			
			//Submit it to the cluster or  locally
			if (null != args && 0 < args.length) {
				config.setNumWorkers(3);
				StormSubmitter.submitTopology(args[0], config, topologyBuilder.createTopology());
			} else {
				config.setMaxTaskParallelism(10);
				final LocalCluster localCluster = new LocalCluster();
				localCluster.submitTopology(TOPOLOGY_NAME, config, topologyBuilder.createTopology());

				Utils.sleep(360 * 1000);

				LOGGER.info("Shutting down the cluster");
				localCluster.killTopology(TOPOLOGY_NAME);
				localCluster.shutdown();
			}
		} catch (final InvalidTopologyException exception) {
			exception.printStackTrace();
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
	}
}

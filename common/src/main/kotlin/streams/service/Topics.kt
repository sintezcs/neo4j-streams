package streams.service

import streams.service.sink.strategy.*
import kotlin.reflect.jvm.javaType

data class Topics(val cypherTopics: Map<String, String> = emptyMap(),
                  val cdcSourceIdTopics: Set<String> = emptySet(),
                  val cdcSchemaTopics: Set<String> = emptySet(),
                  val cudTopics: Set<String> = emptySet(),
                  val nodePatternTopics: Map<String, NodePatternConfiguration> = emptyMap(),
                  val relPatternTopics: Map<String, RelationshipPatternConfiguration> = emptyMap()) {

    fun allTopics(): List<String> = this.asMap()
            .map {
                if (it.key.group == TopicTypeGroup.CDC || it.key.group == TopicTypeGroup.CUD) {
                    (it.value as Set<String>).toList()
                } else {
                    (it.value as Map<String, Any>).keys.toList()
                }
            }
            .flatten()

    fun asMap(): Map<TopicType, Any> = mapOf(TopicType.CYPHER to cypherTopics, TopicType.CUD to cudTopics,
            TopicType.CDC_SCHEMA to cdcSchemaTopics, TopicType.CDC_SOURCE_ID to cdcSourceIdTopics,
            TopicType.PATTERN_NODE to nodePatternTopics, TopicType.PATTERN_RELATIONSHIP to relPatternTopics)

    companion object {
        fun from(config: Map<*, *>, prefix: String, toReplace: String = ""): Topics {
            val cypherTopicPrefix = TopicType.CYPHER.key.replace(prefix, toReplace)
            val sourceIdKey = TopicType.CDC_SOURCE_ID.key.replace(prefix, toReplace)
            val schemaKey = TopicType.CDC_SCHEMA.key.replace(prefix, toReplace)
            val cudKey = TopicType.CUD.key.replace(prefix, toReplace)
            val nodePatterKey = TopicType.PATTERN_NODE.key.replace(prefix, toReplace)
            val relPatterKey = TopicType.PATTERN_RELATIONSHIP.key.replace(prefix, toReplace)
            val cypherTopics = TopicUtils.filterByPrefix(config, cypherTopicPrefix)
            val nodePatternTopics = TopicUtils
                    .filterByPrefix(config, nodePatterKey)
                    .mapValues { NodePatternConfiguration.parse(it.value) }
            val relPatternTopics = TopicUtils
                    .filterByPrefix(config, relPatterKey)
                    .mapValues { RelationshipPatternConfiguration.parse(it.value) }
            val cdcSourceIdTopics = TopicUtils.splitTopics(config[sourceIdKey] as? String)
            val cdcSchemaTopics = TopicUtils.splitTopics(config[schemaKey] as? String)
            val cudTopics = TopicUtils.splitTopics(config[cudKey] as? String)
            return Topics(cypherTopics, cdcSourceIdTopics, cdcSchemaTopics, cudTopics, nodePatternTopics, relPatternTopics)
        }
    }
}

object TopicUtils {

    @JvmStatic val TOPIC_SEPARATOR = ";"

    fun filterByPrefix(config: Map<*, *>, prefix: String): Map<String, String> {
        val fullPrefix = "$prefix."
        return config
                .filterKeys { it.toString().startsWith(fullPrefix) }
                .mapKeys { it.key.toString().replace(fullPrefix, "") }
                .mapValues { it.value.toString() }
    }

    fun splitTopics(cdcMergeTopicsString: String?): Set<String> {
        return if (cdcMergeTopicsString.isNullOrBlank()) {
            emptySet()
        } else {
            cdcMergeTopicsString.split(TOPIC_SEPARATOR).toSet()
        }
    }

    inline fun <reified T: Throwable> validate(topics: Topics) {
        val exceptionStringConstructor = T::class.constructors
                .first { it.parameters.size == 1 && it.parameters[0].type.javaType == String::class.java }!!
        val crossDefinedTopics = topics.allTopics()
                .groupBy({ it }, { 1 })
                .filterValues { it.sum() > 1 }
                .keys
        if (crossDefinedTopics.isNotEmpty()) {
            throw exceptionStringConstructor
                    .call("The following topics are cross defined: $crossDefinedTopics")
        }
    }

    fun toStrategyMap(topics: Topics, sourceIdStrategyConfig: SourceIdIngestionStrategyConfig): Map<TopicType, Any> {
        return topics.asMap()
                .filterKeys { it != TopicType.CYPHER }
                .mapValues { (type, config) ->
                    when (type) {
                        TopicType.CDC_SOURCE_ID -> SourceIdIngestionStrategy(sourceIdStrategyConfig)
                        TopicType.CDC_SCHEMA -> SchemaIngestionStrategy()
                        TopicType.CUD -> CUDIngestionStrategy()
                        TopicType.PATTERN_NODE -> {
                            val map = config as Map<String, NodePatternConfiguration>
                            map.mapValues { NodePatternIngestionStrategy(it.value) }
                        }
                        TopicType.PATTERN_RELATIONSHIP -> {
                            val map = config as Map<String, RelationshipPatternConfiguration>
                            map.mapValues { RelationshipPatternIngestionStrategy(it.value) }
                        }
                        else -> throw RuntimeException("Unsupported topic type $type")
                    }
                }
    }
}
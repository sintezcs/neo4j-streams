package streams.utils

import streams.events.Constraint
import streams.events.StreamsConstraintType
import streams.events.StreamsTransactionEvent
import streams.service.StreamsSinkEntity

object SchemaUtils {
    fun getNodeKeys(labels: List<String>, propertyKeys: Set<String>, constraints: List<Constraint>): Set<String> =
            constraints
                .filter { constraint ->
                    constraint.type == StreamsConstraintType.UNIQUE
                            && propertyKeys.containsAll(constraint.properties)
                            && labels.contains(constraint.label)
                }
                .minBy { it.properties.size }
                ?.properties
                .orEmpty()
//                .ifEmpty { propertyKeys }

    fun getAllRelationshipNodeKeys(propertyKeys: Set<String>, constraints: List<Constraint>): Set<String> =
        constraints
            .filter { constraint ->
                constraint.type == StreamsConstraintType.UNIQUE
                        && propertyKeys.containsAll(constraint.properties)
            }
            .flatMap { it.properties }
            ?.toSet()

    fun toStreamsTransactionEvent(streamsSinkEntity: StreamsSinkEntity,
                                  evaluation: (StreamsTransactionEvent) -> Boolean)
            : StreamsTransactionEvent? = if (streamsSinkEntity.value != null) {
        val data = JSONUtils.asStreamsTransactionEvent(streamsSinkEntity.value)
        if (evaluation(data)) data else null
    } else {
        null
    }

}

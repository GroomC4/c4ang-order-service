package com.groom.order.configuration.jpa

enum class DataSourceType {
    MASTER,
    REPLICA,
    ;

    companion object {
        fun isReadOnlyTransaction(txReadOnly: Boolean): DataSourceType =
            if (txReadOnly) {
                REPLICA
            } else {
                MASTER
            }
    }
}

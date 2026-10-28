@file:JsModule("graphql")

package cz.poqet.walletka.service

import kotlin.js.Promise

/** The few graphql-js entry points the Worker needs. */
internal external fun buildSchema(source: String): dynamic

internal external fun graphql(args: dynamic): Promise<dynamic>

internal external fun parse(source: String): dynamic

internal external fun validate(schema: dynamic, document: dynamic): Array<dynamic>

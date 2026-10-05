package com.studysmart.app.core.data.importer

import org.junit.Assert.assertThrows
import org.junit.Test

class StrictJsonTest {
    @Test fun standardJsonAndEscapesAreAccepted() {
        validateStrictJson("""{"a":[true,false,null,-2,1.2e+3],"s":"a\n\u0041\\\""}""")
    }

    @Test fun nonstandardSyntaxAndTrailingContentAreRejected() {
        for (text in listOf("{} trailing", "{'a':1}", "{a:1}", "{\"a\":1,}", "[1,]", "[01]", "[+1]", "[NaN]", "/*comment*/{}", "{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}")) {
            assertThrows(text, IllegalArgumentException::class.java) { validateStrictJson(text) }
        }
    }
}

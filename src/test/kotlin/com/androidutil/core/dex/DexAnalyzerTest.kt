package com.androidutil.core.dex

import com.androidutil.TestApkBuilder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream

class DexAnalyzerTest : DescribeSpec({

    val analyzer = DexAnalyzer()

    describe("analyze") {
        it("should parse method, class, and string counts from valid DEX") {
            val dexBytes = TestApkBuilder.createDexFile(
                methodCount = 1500,
                classCount = 200,
                stringCount = 3000
            )
            val result = analyzer.analyze(
                ByteArrayInputStream(dexBytes),
                "classes.dex",
                dexBytes.size.toLong()
            )

            result.name shouldBe "classes.dex"
            result.methodCount shouldBe 1500
            result.classCount shouldBe 200
            result.stringCount shouldBe 3000
            result.sizeBytes shouldBe dexBytes.size.toLong()
        }

        it("should handle file too small") {
            val tinyBytes = ByteArray(50)
            val result = analyzer.analyze(
                ByteArrayInputStream(tinyBytes),
                "classes.dex",
                50L
            )

            result.methodCount shouldBe 0
            result.classCount shouldBe 0
            result.stringCount shouldBe 0
        }

        it("should handle invalid magic bytes") {
            val badBytes = ByteArray(112) { 0 }
            val result = analyzer.analyze(
                ByteArrayInputStream(badBytes),
                "classes.dex",
                112L
            )

            result.methodCount shouldBe 0
            result.classCount shouldBe 0
        }

        it("should parse multiple DEX files with different counts") {
            val dex1 = TestApkBuilder.createDexFile(methodCount = 100, classCount = 20, stringCount = 50)
            val dex2 = TestApkBuilder.createDexFile(methodCount = 65536, classCount = 5000, stringCount = 30000)

            val r1 = analyzer.analyze(ByteArrayInputStream(dex1), "classes.dex", dex1.size.toLong())
            val r2 = analyzer.analyze(ByteArrayInputStream(dex2), "classes2.dex", dex2.size.toLong())

            r1.methodCount shouldBe 100
            r2.methodCount shouldBe 65536
            r2.classCount shouldBe 5000
        }
    }
})

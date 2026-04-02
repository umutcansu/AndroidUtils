package com.androidutil.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class FileSizeTest : DescribeSpec({

    describe("format") {
        it("should format bytes under 1KB") {
            FileSize.format(0) shouldBe "0 B"
            FileSize.format(1) shouldBe "1 B"
            FileSize.format(512) shouldBe "512 B"
            FileSize.format(1023) shouldBe "1023 B"
        }

        it("should format kilobytes") {
            FileSize.format(1024) shouldBe "1.0 KB"
            FileSize.format(1536) shouldBe "1.5 KB"
            FileSize.format(10240) shouldBe "10.0 KB"
        }

        it("should format megabytes") {
            FileSize.format(1048576) shouldBe "1.0 MB"
            FileSize.format(5242880) shouldBe "5.0 MB"
            FileSize.format(1572864) shouldBe "1.5 MB"
        }

        it("should format gigabytes") {
            FileSize.format(1073741824) shouldBe "1.0 GB"
            FileSize.format(2147483648) shouldBe "2.0 GB"
        }

        it("should cap at GB for very large values") {
            // 10 GB should still show GB
            FileSize.format(10737418240) shouldBe "10.0 GB"
        }
    }

    describe("formatDetailed") {
        it("should include both formatted and raw bytes") {
            FileSize.formatDetailed(1048576) shouldBe "1.0 MB (1048576 bytes)"
            FileSize.formatDetailed(512) shouldBe "512 B (512 bytes)"
        }
    }

    describe("percentage") {
        it("should calculate percentage correctly") {
            FileSize.percentage(50, 100) shouldBe "50.0%"
            FileSize.percentage(1, 3) shouldBe "33.3%"
            FileSize.percentage(100, 100) shouldBe "100.0%"
        }

        it("should handle zero total") {
            FileSize.percentage(0, 0) shouldBe "0.0%"
            FileSize.percentage(50, 0) shouldBe "0.0%"
        }

        it("should handle zero part") {
            FileSize.percentage(0, 100) shouldBe "0.0%"
        }
    }
})

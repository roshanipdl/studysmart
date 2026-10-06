plugins { kotlin("jvm") version "2.3.21" }

kotlin { jvmToolchain(21) }
dependencies {
    implementation("androidx.room:room-common:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Android supplies org.json at runtime; this dependency is only for this JVM verification harness.
    implementation("org.json:json:20250517")
    testImplementation("junit:junit:4.13.2")
}
sourceSets {
    main {
        kotlin.srcDir("../../app/src/main/java")
        kotlin.include("com/studysmart/app/core/model/**")
        kotlin.include("com/studysmart/app/core/database/CurriculumEntities.kt")
        kotlin.include("com/studysmart/app/core/database/SearchEntities.kt")
        kotlin.include("com/studysmart/app/core/database/SearchDocumentProjector.kt")
        kotlin.include("com/studysmart/app/core/database/SearchDao.kt")
        kotlin.include("com/studysmart/app/core/data/Search*.kt")
        // The transactional repository requires the Android Room runtime.
        kotlin.exclude("com/studysmart/app/core/data/SearchRepository.kt")
        kotlin.include("com/studysmart/app/core/common/DispatcherProvider.kt")
        kotlin.include("com/studysmart/app/core/common/AnswerContentParser.kt")
        kotlin.include("com/studysmart/app/core/database/TestEntities.kt")
        kotlin.include("com/studysmart/app/core/database/CurriculumImportStateEntity.kt")
        kotlin.include("com/studysmart/app/core/database/CurriculumImportDao.kt")
        kotlin.include("com/studysmart/app/core/data/importer/CurriculumImportModels.kt")
        kotlin.include("com/studysmart/app/core/data/importer/CurriculumBundleSource.kt")
        kotlin.include("com/studysmart/app/core/data/importer/CurriculumJsonParser.kt")
        kotlin.include("com/studysmart/app/core/data/importer/CurriculumImportValidator.kt")
        kotlin.include("com/studysmart/app/core/data/importer/CurriculumImportEngine.kt")
        kotlin.include("com/studysmart/app/core/data/importer/StrictJson.kt")
    }
    test {
        kotlin.srcDir("../../app/src/test/java")
        kotlin.include("com/studysmart/app/core/data/importer/**")
        kotlin.include("com/studysmart/app/core/common/AnswerContentParserTest.kt")
        kotlin.include("com/studysmart/app/core/database/SearchDocumentProjectorTest.kt")
        kotlin.include("com/studysmart/app/core/data/SearchQueryTest.kt")
        kotlin.include("com/studysmart/app/core/data/SearchRankingTest.kt")
    }
}
tasks.test {
    inputs.dir(file("../../curriculum/database-stage-1")).withPropertyName("curriculumSnapshot")
    inputs.dir(file("../../curriculum/database-stage-1-reviewed")).withPropertyName("curriculumReviewed")
    inputs.dir(file("../../curriculum/database-stage-1-formatted")).withPropertyName("curriculumFormatted")
    systemProperty("curriculum.snapshot", file("../../curriculum/database-stage-1").absolutePath)
    systemProperty("curriculum.reviewed", file("../../curriculum/database-stage-1-reviewed").absolutePath)
    systemProperty("curriculum.formatted", file("../../curriculum/database-stage-1-formatted").absolutePath)
}

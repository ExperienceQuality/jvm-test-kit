package com.xq.jvmtestkit.conventions

import org.gradle.api.file.DirectoryProperty

abstract class PublishingConventionsExtension {
    /** Local repository used by CI staging and plugin TestKit builds. */
    abstract DirectoryProperty getTestRepository()
}

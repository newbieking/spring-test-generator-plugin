package com.newbieking.springtestgen.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiManager

/**
 * 将生成的测试类内容写入 src/test/java 目录的公共工具。
 * 调用方需自行确保在 WriteCommandAction 内执行。
 */
object TestFileWriter {

    private val log = Logger.getInstance(TestFileWriter::class.java)

    /**
     * 写入测试文件，若文件已存在则更新，否则新建。
     *
     * @param project         当前项目
     * @param testClassName   测试类名 (如 "MyServiceTest")
     * @param testPackageName 测试包名 (如 "com.example.service.test")
     * @param content         生成的测试类源码
     * @return true 表示写入成功，false 表示目录不存在
     */
    fun writeTestFile(
        project: Project,
        testClassName: String,
        testPackageName: String,
        content: String
    ): Boolean {
        val baseDir = project.baseDir
        val testSrcDir = baseDir.findChild("src")?.findChild("test")?.findChild("java")
        if (testSrcDir == null) {
            log.warn("Unable to write generated test '$testClassName': src/test/java directory not found in ${project.basePath}")
            Messages.showWarningDialog(
                project,
                "Could not find src/test/java directory. Please ensure your project has a standard Maven/Gradle layout.",
                "Spring Test Generator"
            )
            return false
        }

        val psiDir = PsiManager.getInstance(project).findDirectory(testSrcDir)
        if (psiDir == null) {
            log.warn("Unable to resolve src/test/java as PSI directory")
            return false
        }

        val packageDir = testPackageName.split('.')
            .filter(String::isNotBlank)
            .fold(psiDir) { directory, segment ->
                directory.findSubdirectory(segment) ?: directory.createSubdirectory(segment)
            }
        val fileName = "$testClassName.java"
        val existingFile = packageDir.findFile(fileName)
        if (existingFile != null) {
            PsiUtils.setContent(existingFile, content)
            log.info("Updated generated test file: ${existingFile.virtualFile.path}")
        } else {
            packageDir.createFile(fileName).let {
                PsiUtils.setContent(it, content)
                log.info("Created generated test file: ${it.virtualFile.path}")
            }
        }
        return true
    }

    /**
     * 根据类的全限定名推导测试包名，默认为原包名 + ".test"。
     */
    fun deriveTestPackageName(qualifiedName: String?): String {
        val packageName = qualifiedName?.substringBeforeLast('.', "") ?: ""
        return if (packageName.isBlank()) "test" else "$packageName.test"
    }
}

package kr.kennysoft.idea260828korean.services

import com.intellij.openapi.project.Project
import kr.kennysoft.idea260828korean.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}

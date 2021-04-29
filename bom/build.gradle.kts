dependencies {
    constraints {
        rootProject.subprojects.forEach {
            if (it.name == "rest-api") {
                return@forEach
            }
            api(it)
        }
    }
}

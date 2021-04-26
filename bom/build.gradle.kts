dependencies {
    constraints {
        rootProject.subprojects.forEach {
            if (it.name.equals("rest-api")) {
                return@forEach
            }
            api(it)
        }
    }
}

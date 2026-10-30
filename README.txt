Admin Checker (Fabric, Minecraft Java 26.2)
===========================================
Требуется: JDK 25, Gradle 9.5+ (или Gradle wrapper из fabric-example-mod).

Сборка:
  1. Если нет gradlew:   gradle wrapper --gradle-version 9.5.1
  2. ./gradlew build        (Windows: gradlew.bat build)
  3. Готовый мод:  build/libs/admin-checker-1.0.0.jar
  4. Положи jar + Fabric API 26.2 в папку mods.

Игра:  /mo  — включить/выключить список в правом нижнем углу.
Ники и цвета меняются в начале AdminCheckerClient.java.

Нет возможности собрать у себя? Залей папку на GitHub (репозиторий) —
вкладка Actions -> build -> Artifacts -> admin-checker-jar. Внутри готовый .jar.

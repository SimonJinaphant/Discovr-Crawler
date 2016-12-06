# Discovr-Crawler
Web crawler for [Discovr](https://github.com/SimonJinaphant/Discovr), a personalized UBC companion app.

### Setting up
This project is a standard Java repository with Gradle for its build/dependency management; by cloning this repo with a good IDE that supports Gradle (like IntelliJ) all of the external JARs should be automatically downloaded as well

1. Open IntelliJ and select `Check out project from Version Control`. If you're inside a project already you can find yourself back to the home screen by choosing `File -> Close Project`
2. Select `GitHub` and enter your Github credentials.
3. For the Git Repository URL enter: `https://github.com/SimonJinaphant/Discovr-Crawler.git`. You can leave the other fields blank.
4. It may take a couple of minutes for Gradle to finish building for the first time
   - If you run into errors regarding gradle build tools see the fix at the bottom.
   - You'll likely get an `Error Loading Project` because two modules (.iml files) don't exist. Ignore this error by selecting the remove option.
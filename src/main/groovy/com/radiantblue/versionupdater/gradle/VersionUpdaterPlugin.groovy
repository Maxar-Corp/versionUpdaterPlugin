package com.radiantblue.versionupdater.gradle;

import org.gradle.api.*;
import org.gradle.api.plugins.WarPlugin

class VersionUpdaterPlugin implements Plugin<Project> {
   def void apply(Project project) {
      project.task('updateTheVersion') << {

         def String  basePath = project.projectDir
         def String  rootPath = project.rootDir
         def String  projectPath = basePath.substring(rootPath.size() + 1)
         def String  gitUpdatePath = projectPath + "/gradle.properties"

         if (!basePath.contains(rootPath)) {
            println "Expected ejection for ${basePath}"
            project.ext.set("doVersionUpdateCommit", "false")
            return
         }

         //println "    Root project:  ${project.rootProject}"
         //println "         rootDir:  ${project.rootDir}"
         //println "      projectDir:  ${project.projectDir}"
         //println " User projectDir:  ${System.getProperty("user.dir")}"

         // Defines
         String RELEASE_STRING = "RELEASE_UPDATE"
         String BRANCH_TO_PROCESS = "master"
   
         String lastCommitFromFile = "NA"
         String buildVersion = "NA"


         // Read in the gradle.properties for this project (make sure it exists)
         File file = new File("${project.projectDir}/gradle.properties")
         if (file.exists()) {
            // Read all the lines from our gradle.properties file
            def lines = file.readLines()
            lines.each { String line ->
               // Find the 'lastCommit' line and store the token
               if (line.startsWith("lastCommit")) {
                  def tokens = line.split('=')
                  lastCommitFromFile = tokens[1]
               }

               // Find the 'buildVersion' line and store the token
               if (line.startsWith("buildVersion")) {
                  def tokens = line.split('=')
                  buildVersion = tokens[1]
                  println "-----Current release version: ${buildVersion} for ${projectPath}"
               }
            }

            // Retrieve the git branch information for this project
            def cmdBranch = "git --git-dir=${project.rootDir}/.git rev-parse --abbrev-ref HEAD"
            String branch = cmdBranch.execute().text.trim()

            // Since we have to commit this gradle.properties file, it will change the real last git
            //   commit ID. However, this commit will always have the comment ${RELEASE_STRING} so we will
            //   ignore the commitID for it and use the one before it. Attempt to pull the lines from the log
            def cmdLog = "git --git-dir=${project.rootDir}/.git log --oneline -3 --format=%h:%s"
            String log = cmdLog.execute().text.trim()
            def logLines = log.split('\n')

            def commitTokenCount = 0
            def tokens = new String[logLines.size()][2]
            for (int i = 0; i < logLines.size(); i++) {
               def lineTokens = logLines[i].split(/\:/)
               for (int j = 0; j < lineTokens.size(); j++) {
                  tokens[i][j] = lineTokens[j]
                  ++commitTokenCount
               }
            }

            def lastCodeCommitId
            def versionReleaseCommitId

            // If the first line (first 2 tokens) were our expected RELEASE_STRING, then there is nothing to do.
            //   Otherwise, it will be the last code commit ID indicating we need to update the version.
            // println "Comparing: ${tokens[0][1]} and ${RELEASE_STRING}"
            if (tokens[0][1] != RELEASE_STRING) {
               // This was a legitimate code commit, so use the short commit ID here
               lastCodeCommitId = tokens[0][0]
            }
            else {
               println "     INFO: No code changes committed - Nothing to do for ${projectPath}"

               // Eject
               return
            }
   
            // We are only going to continue this process if this is the master branch.
            if (branch != BRANCH_TO_PROCESS) {
               println "Not updating versions for branch ${branch}!"
            }
            else {
               // If the lastCommit in the properties file isn't the same as the most current commit id, 
               //   then we know something changed for this release so we are going to update the minor version
               //   number (for now).
               if (!lastCommitFromFile.trim().startsWith(lastCodeCommitId.trim())) {
                  println "lastCommitFromFile: ${lastCommitFromFile}"
                  println "lastCodeCommitId: ${lastCodeCommitId}"

                  println "     INFO: Last code commit IDs have changed. The version number will be updated."
                  def replacementFile = new File("${project.projectDir}/gradle.properties")
                  def oldCommitLine = "lastCommit=${lastCommitFromFile}"
                  def newCommitLine = "lastCommit=${lastCodeCommitId}"

                  // Split on period. Need to escape it in groovy...
                  def versionTokens = buildVersion.split(/\./)

                  // Figure out how many tokens we have and which is the last one
                  def numVersionTokens = versionTokens.size()
                  def lastTokenIndex = numVersionTokens - 1

                  // This will update the minor number for this release. We don't know if it has
                  //   2, 3 or 4 numbers but we really don't care. As long as we update the last one
                  //   the build number will change. The last one is always 1 less than numVersionTokens.
                  int minorNumber = versionTokens[numVersionTokens - 1].toInteger()
                  minorNumber +=1

                  // Replace the minor number with our new value
                  versionTokens[lastTokenIndex] = Integer.toString(minorNumber)
                       
                  def oldVersionLine = "buildVersion=${buildVersion}"
                  def newVersionLine = "buildVersion="
                  def tmp = versionTokens[0]
                  for (int i = 1; i < numVersionTokens; i++) {
                     // Build in version separator
                     tmp += "."
                     tmp += versionTokens[i]
                  }

                  println "+++++Updating release version to: ${tmp} for ${projectPath}"

                  newVersionLine += tmp

                  // This block will rewrite the file with the new information
                  def newConfig = replacementFile.text.replace(oldCommitLine, newCommitLine)
                  newConfig = newConfig.replace(oldVersionLine, newVersionLine)
                  replacementFile.text = newConfig

                  // Now we have to actually commit the gradle.properties file with this new information
                  def filename = "${rootPath}/.git/index.lock"
                  def lockfile = new File(filename)

                  while (lockfile.exists()) {
                     sleep(2000)
                  }

                  // There is a concurrent git access lock problem that we have to make sure we wait for
                  boolean isDone = false
                  while (!isDone) {
                     // Set up stderr and stdout for our first process
                     def sout = new StringBuilder(), serr = new StringBuilder()
                     def addProc = "git --git-dir=${project.rootDir}/.git add ${gitUpdatePath}".execute()
                     addProc.consumeProcessOutput(sout, serr)
                     addProc.waitFor()
                     // If it failed (serr's size > 0) then inform the user something went wrong
                     if (serr.size() > 0) {
                        sleep (100)
                     }
                     else {
                        isDone = true
                     }
                  }

                  //def gitCmd = "git --git-dir=${project.rootDir}/.git add ${gitUpdatePath}"
                  //gitCmd.execute().text.trim()
         
                  // Reset stderr and stdout
                  //sout = new StringBuilder()
                  //serr = new StringBuilder()
                  //while (lockfile.exists) {
                     //sleep(100)
                  //}
                  //def commitProc = "git --git-dir=$rootPath/.git commit -m $RELEASE_STRING".execute()
                  //commitProc.consumeProcessOutput(sout, serr)
                  //commitProc.waitForOrKill(20000)

                  // If it failed (serr's size > 0) then inform the user something went wrong
                  //if (serr.size() > 0) {
                     //println "stderr > $serr"
                  //}
               }
               else {
                  println "     INFO: Last code commit IDs are equal - nothing to update."
               }
            }
         }
         else {
            println "     INFO: No gradle.properties to update for ${projectPath}"
         }
      }
   }
}

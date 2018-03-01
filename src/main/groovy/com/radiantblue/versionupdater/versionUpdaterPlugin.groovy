package com.radiantblue.versionupdater;

import org.gradle.api.*;
import org.gradle.api.plugins.WarPlugin

class VersionUpdaterPlugin implements Plugin<Project> {
   def void apply(Project project) {
      project.task('updateTheVersion') {
         doLast {

         try {
            // This SHOULD be the location of where the gradle.properties file 
            //   is for the app
            String projectPath
            // This SHOULD be the location of the .git folder
            String gitRootPath

            boolean checkForGradlePropertiesInBase = false

            // Extract some information from the project argument
            def String  basePath = project.projectDir
            def String  rootPath = project.rootDir

            // Prepare for jenkins project kluge! The rootPath shouldn't really 
            //   contain full paths to subprojects. It should be the path to the
            //   location of the .git directory. If this jenkins project 
            //   variable comes in this way, we need to hack it to where the
            //   .git folder ACTUALLY lives.
            if (rootPath.contains("/apps/")) {
               // Extract the base portion of this path for the REAL rootPath
               gitRootPath = rootPath.substring(0, rootPath.indexOf("/apps/"))
            }
            else {
               gitRootPath = rootPath
            }

            // Make sure this isn't just a single directory before attempting 
            //   to isolate an app directory.
            if (basePath.size() > rootPath.size()) {
               projectPath = basePath.substring(rootPath.size() + 1)
            }
            else {
               projectPath = rootPath
               checkForGradlePropertiesInBase = true
            }
println "============>>>>>>>>> Using GIT ROOT PATH: ${gitRootPath}"

            boolean pluginFileExists = false
            boolean gradlePropertiesInBase = false

            println "=========================================================="
            println "=========================================================="
            println "  Jenkins Root project:  ${project.rootProject}"
            println "       Jenkins rootDir:  ${project.rootDir}"
            println "    Jenkins projectDir:  ${project.projectDir}"
            println "       User gitRootDir:  ${rootPath}"
            println "      User projectPath:  ${projectPath}"
            println "       User projectDir:  ${System.getProperty("user.dir")}"

            // Is there a gradle properties in this base project?
            if (checkForGradlePropertiesInBase ) {
               File gradleBaseFile = new File(
                  "${project.projectDir}/gradle.properties")

               if (gradleBaseFile.exists()) {
                  println "INFO: gradle.properties found in base " +
                     "root project '${rootPath}'"
                  gradlePropertiesInBase = true
               }
               else {
                  println "INFO: No gradle.properties found in base root " +
                     "project '${rootPath}'"
                  project.ext.set("doVersionUpdateCommit", "false")
                  println "=========================================================="
                  println "=========================================================="
                  return
               }
            }
   
            if (!gradlePropertiesInBase && !basePath.contains("app")) {
               println "Quietly exiting '${project.projectDir}' is not a " +
                  "versionable application project."
               project.ext.set("doVersionUpdateCommit", "false")
               println "=========================================================="
               println "=========================================================="
               return
            }

            // Retrieve the token to split paths on. This is necessary because 
            //   of the way Jenkins in the cloud has data partitions mounted.
            String splitToken = project.ext.versionPathSplitToken

            // If we have a splitToken defined (which we must in the Jenkins 
            //   environment) use it to split paths
            if (splitToken != null && splitToken.length() > 0) {
                // Split the root path first
                String []rootTokens = rootPath.split(splitToken)
                // Expecting 2 pieces of this path
                if (rootTokens != null && rootTokens.size() == 2) {
                   rootPath = rootTokens[1]

                   // Split the project path next
                   String []pathTokens = basePath.split(splitToken)
                   // Again, we are expecting 2 pieces of this path
                   if (pathTokens != null && pathTokens.size() == 2) {
                      basePath = pathTokens[1]
                      if (basePath.size() > rootPath.size()) {
                         projectPath = basePath.substring(rootPath.size() + 1)
                      }
                      else {
                         projectPath = basePath
                      }

                      println "Updated basePath: ${basePath}"
                      println "Updated rootPath: ${rootPath}"
                      println "Updated projectPath: ${projectPath}"
                      println "=========================================================="
                      println "=========================================================="
                   }
                   else {
                      println "     ERROR: Could not split projectPath with " +
                         "${splitToken} - " + pathTokens.size()
                      project.ext.set("doVersionUpdateCommit", "false")
                      println "=========================================================="
                      println "=========================================================="
                      return
                   }
                }
                else {
                   println "     ERROR: Could not split rootPath with " + 
                      "${splitToken} - " + rootTokens.size()
                   project.ext.set("doVersionUpdateCommit", "false")
                   println "=========================================================="
                   println "=========================================================="
                   return
                }
            }
            else {
               println "INFO: No path split token required."
               println "=========================================================="
               println "=========================================================="
            }

            // If this projectDir (basePath) does not contain the base git repo 
            //   (rootPath) then we can safely set the do commit flag to false 
            //   because this project is not in the base git repo. We can also 
            //   quietly return out of this task.
            if (!basePath.contains(rootPath)) {
               project.ext.set("doVersionUpdateCommit", "false")
               return
            }

            // The "well-known" git comment to use when updating a newly 
            //   versioned gradle.properties
            String RELEASE_STRING = "RELEASE_UPDATE"

            // Pull the branch we need to check from 
            //   omar-common-properties.gradle
            String BRANCH_TO_PROCESS = project.ext.versionBranchToCheck
            println "-----Checking branch '${BRANCH_TO_PROCESS}' " +
               "for code updates..."
   
            // Default these to something
            String lastCommitFromFile = "NA"
            String lastCodeCommitId = "NA"
            String buildVersion = "NA"

            // Read in the gradle.properties for this project 
            //   (make sure it exists)
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
                     println "-----Current release version: ${buildVersion} " +
                        "for ${projectPath}"
                  }
               }

               // Determine if this project has a plugins path also...
               String tmpPath = project.projectDir
               String pluginFilePath = tmpPath.replace("/apps/", "/plugins/")
               tmpPath = pluginFilePath.substring(0, pluginFilePath.length() - 3)
               pluginFilePath = tmpPath + "plugin"
               File pifile = new File("${pluginFilePath}/gradle.properties")
               if (pifile.exists()) {
                  pluginFileExists = true
               }

//TODO

               // Retrieve the git branch information for this project
               def cmdBranch = 
                  "git --git-dir=${gitRootPath}/.git rev-parse --abbrev-ref HEAD"
               String branch = cmdBranch.execute().text.trim()
   
               // We are only going to continue this task if this is the branch 
               //   we are supposed to check
               if (branch != BRANCH_TO_PROCESS) {
                  println "INFO: Not updating versions for branch '${branch}'"
   
                  // Set the flag to indicate a commit is not necessary
                  project.ext.set("doVersionUpdateCommit", "false")
   
                  // Eject now...
                  return
               }

               // Since we have to commit gradle.properties files during our 
               //   buildVersion updates, it will change the real last git 
               //   commit ID. However, this commit will always have the comment
               //   RELEASE_UPDATE so we will ignore the commitID for it and use
               //   the one after it. Attempt to pull the last 5 log lines.
               //   Formatted as <hex value of short commit ID>:<commit message>
               def cmdLog = 
                 "git --git-dir=${gitRootPath}/.git log --oneline -5 --format=%h:%s"
               String log = cmdLog.execute().text.trim()
   
               // Split the last log lines by the newline character to make a 
               //   2D String tokens matrix
               def logLines = log.split('\n')
   
               // Default our LCVs
               int lastCommitIdx = -1; 
               boolean foundLastCommit = false;

               // Allocate our container for the tokens of however many log 
               //   lines we could retrieve
               def tokens = new String[logLines.size()][2]
   
               // Loop through all the loglines and store the tokens 
               //   in our matrix
               for (int i = 0; i < logLines.size(); i++) {
                  // Split on the colon (escaped) character
                  def lineTokens = logLines[i].split(/\:/)
                  for (int j = 0; j < lineTokens.size(); j++) {
                     tokens[i][j] = lineTokens[j]
                  }

                  // If we haven't yet found the last real commit ID that wasn't
                  //   for a RELEASE_UPDATE and this one now "fits the bill" 
                  //   then store this row index and set the flag to
                  //   show we found the last commit ID.
                  if (!foundLastCommit && tokens[i][1] != "RELEASE_UPDATE") {
                     foundLastCommit = true;
                     lastCommitIdx = i;
                     //println "Retrieved a lastCommit that was not a RELEASE_UPDATE: '${tokens[i][0]}:${tokens[i][1]}'"
                  }
               }

               if (lastCommitFromFile == "NA" || lastCommitIdx == -1) {
                  // There was no lastCommit token in the project
                  println "INFO: No last commit token found in '${projectPath}'"
   
                  // Set the flag to indicate a commit is not necessary
                  project.ext.set("doVersionUpdateCommit", "false")
                  return 
               }

               // If the the lastCommitFromFile (read from the gradle.properties 
               //   file) doesn't match what we just parsed out of the git log, 
               //   then we know we had a code change to this branch since it was last
               //   built and deployed.
               if (lastCommitFromFile != tokens[lastCommitIdx][0])
               {
                  // This was a legitimate code commit, so use the short commit ID here
                  lastCodeCommitId = tokens[lastCommitIdx][0]
               }
               else {
                  println "INFO: No new code changes committed - Nothing to " +
                     "update for '${projectPath}'"
   
                  // Set the flag to indicate a commit is not necessary
                  project.ext.set("doVersionUpdateCommit", "false")
   
                  // Eject now...
                  return
               }
   
               // If the lastCommit in the properties file isn't the same as the 
               //   most current commit id, then we know something changed for 
               //   this release so we are going to update the minor version
               //   number (for now).
               if (!lastCommitFromFile.trim().startsWith(lastCodeCommitId.trim())) {
                  def oldCommitLine = "lastCommit=${lastCommitFromFile}"
                  def newCommitLine = "lastCommit=${lastCodeCommitId}"
   
                  // Split on period. Need to escape it in groovy...
                  def versionTokens = buildVersion.split(/\./)
   
                  // Figure out how many tokens we have and which is the last one
                  def numVersionTokens = versionTokens.size()
                  def lastTokenIndex = numVersionTokens - 1
   
                  // This will update the minor number for this release. We 
                  //   don't know if it has 2, 3 or 4 numbers but we really 
                  //   don't care. As long as we update the last one the build 
                  //   number will change. The last one is always 1 less than 
                  //   numVersionTokens.
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

                  println "+++++INFO: lastCommit token has changed. Version " +
                     "number(s) will be updated to ${tmp} for ${projectPath}"
   
                  // Concat the number with the token key
                  newVersionLine += tmp
   
                  // Build the string path to the apps and plugins 
                  //   gradle.properties files
                  String appUpdatePath = projectPath + "/gradle.properties"
                  String pluginUpdatePath =  pluginFilePath + "/gradle.properties"
   
                  // This will be the base git add command. It may also get the 
                  //   pluginUpatePath appended later.

                  String addCommand

                  if (!gradlePropertiesInBase) { 
                     addCommand = "git --git-dir=${gitRootPath}/.git " +
                        "add ${appUpdatePath}"
                  }
                  else {
                     addCommand = "git --git-dir=${gitRootPath}/.git " +
                        "--work-tree=${gitRootPath} " +
                        "add gradle.properties"
                  }
   
                  // Update the necessary lines in both files if they exist. If 
                  //   we make it here, the app gradle.properties exists for sure.
                  updateGradlePropertiesFile(appUpdatePath, oldVersionLine, newVersionLine, 
                     oldCommitLine, newCommitLine)
                  // Does the plugin file exist?
                  if (pluginFileExists) {
                     updateGradlePropertiesFile(pluginUpdatePath, 
                        oldVersionLine, newVersionLine, 
                        oldCommitLine, newCommitLine)

                     // Append this file to the git add command so we can 
                     //   add/commit both at once
                     if (!gradlePropertiesInBase) { 
                        addCommand += " ${pluginUpdatePath}"
                     }
                     else {
                        addCommand += " ../../plugins/${project.rootProject}/gradle.properties"
                     }
                  }

println "ADDCOMMAND: ${addCommand}"

                  // The parallel build of gradle will sometimes cause these git 
                  //   lock files to exist if more than 1 build is operating 
                  //   with git on the same git repo. This check will wait until 
                  //   that lock is freed up.
                  def filename = "${gitRootPath}/.git/index.lock"
                  lockFileWait(filename)

                  // There is a concurrent git access lock problem that we have 
                  //   to make sure we wait for, use this flag to retry until 
                  //   the file(s) are added successfully in git.
                  boolean isDone = false

                  // Now we have to actually 'git add' the gradle.properties 
                  //   file(s) with this new information
                  while (!isDone) {
                     // Set up stderr and stdout for our process
                     def sout = new StringBuilder(), serr = new StringBuilder()
                     def addProc = addCommand.execute()
                     addProc.consumeProcessOutput(sout, serr)
                     addProc.waitFor()
                     // If it failed (serr's size > 0) then inform the user 
                     //   something went wrong
                     if (serr.size() > 0) {
                        // Something went wrong, wait a little and try it again
                        sleep (100)
                     }
                     else {
                        // The add worked. We're done so set the flag to get 
                        //   out of this loop.
                        isDone = true
                     }
                  }
               }
               else {
                  println "INFO: The lastCommit tokens are current - nothing " +
                     "to update for '${projectPath}'"
   
                  // Set the flag to indicate a commit is not necessary
                  project.ext.set("doVersionUpdateCommit", "false")
               }
            }
            else {
               // This project does not contain versioning information
               println "INFO: No gradle.properties to update for '${projectPath}'"
   
               // Set the flag to indicate a commit is not necessary
               project.ext.set("doVersionUpdateCommit", "false")
            }
         }
         catch (Exception err) {
            // Keeping this in to see possible exceptions in the process
            def sw = new StringWriter()
            def pw = new PrintWriter(sw)
            err.printStackTrace(pw)
            println sw.toString()
   
            // Set the flag to indicate a commit is not necessary
            project.ext.set("doVersionUpdateCommit", "false")
         }

         } // doLast
      } // task
   } // apply

   static boolean updateGradlePropertiesFile(String filePathToUpdate, 
      String oldVersionLine, String newVersionLine, 
      String oldCommitLine, String newCommitLine) {
      boolean retVal = true

      // Get the File handle
      def replacementFile = new File(filePathToUpdate)
println "FILEPATHTOUPDATE: ${filePathToUpdate}"

      if (replacementFile.exists()) {
         // This block will rewrite the file with the new information
         def newConfig = replacementFile.text.replace(
            oldCommitLine, newCommitLine)
         newConfig = newConfig.replace(oldVersionLine, newVersionLine)
         replacementFile.text = newConfig
      }
      else {
         println "File ${filePathToUpdate} does not exist. Cannot update " +
            "version/commit information."
         retVal = false
      }

      return retVal
   }

   static void lockFileWait(String filename) {
      def lockfile = new File(filename)
      def lockTries = 0

      while (lockfile.exists() && ++lockTries < 10) {
         sleep(2000)
      }

      if (lockTries == 10) {
         println "WARNING: Max git unlock tries exceeded for '${filename}'"
      }
   }
}

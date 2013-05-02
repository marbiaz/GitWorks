To run the code java -Xmx512M -cp <bin-dir>:<lib-dir> gitworks.GitWorks <arg-list>

Needed libraries in <lib-dir> : org.eclipse.jgit-2.1.0.201209190230-r.jar jsch-0.1.49.jar

The <arg-list> must be composed as folllows:
<repo list file path> <repo dir path> <jgit gits out dir> <jgit trees out dir> <comma-separated no-space list of fork ids>
where:
<repo list file path> is the absolute path to the file containing the list of repos we want to analyze (from lucene or flossmole, see .raw files in the exp.git repo)
<repo dir path> is the absolute path to the dir that contains the git repos to be imported in jgit data structures (input)
<jgit gits out dir> is the relative path to the dir which will contain the jgit-generated git repos to analyse (output)
<jgit trees out dir> is the relative path to the dir which will contain the jgit-generated trees of the repos (output)
<comma-separated no-space list of fork ids> is the list of projects ids for the repos which we want to compute aggregate stats (see ids definition in the code).


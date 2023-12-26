
# Tool to get dependencies required to build a target

This tool gives the dependencies required to build a target by reading import statements in java files. This tool works for targets which contains java src files.




## Run Locally
### Prerequisite

1. Install python 3.12: Python 3.12 can be installed using a [pyenv](https://github.com/pyenv/pyenv) which is a version manager for python.

Run below comands from the root of harness-core

### Run the script:

```bash
  python3 tools/build-optimization/src/get_build_target.py
```

**Input:** The input to this script is the absolute path address of a directory from where to read the java files.

Then optionally to filter the results, module's path relative to harness-core can be provided.

**Output:** After completing the search, the process returns a list of build targets that are required for building the module. These build targets are typically specified in the "BUILD.bazel" files and represent the dependencies needed to compile and build the module successfully.


## Usage/Examples

Enter the directory name
```bash
Enter the directory to scan for Java files: /Users/pankajkumar/Desktop/harness-core/950-delegate-tasks-beans/src/main/java
```

Filter the results (optional)
```bash
Enter the directory name to filter results: 960-api-services
```
Output
```bash
"//960-api-services/src/main/java/io/harness/artifacts/azureartifacts/beans:module",
"//960-api-services/src/main/java/io/harness/artifacts/docker/service:module",
"//960-api-services/src/main/java/io/harness/artifacts/jenkins/beans:module",
"//960-api-services/src/main/java/io/harness/aws/beans:module",
"//960-api-services/src/main/java/io/harness/azure/client:module",
"//960-api-services/src/main/java/io/harness/git:module",
"//960-api-services/src/main/java/io/harness/k8s/model:module",
"//960-api-services/src/main/java/io/harness/security/encryption:module",
"//960-api-services/src/main/java/io/harness/ssh:module",
```
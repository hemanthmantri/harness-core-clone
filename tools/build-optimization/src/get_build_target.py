# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import os
import re
import index_files_full_address
import get_bazel_directories

# extracts imports from java file
def extract_imports(file_path):
    import_pattern = re.compile(r'^import\s+(static\s+)?([\w.]+);')

    imports = []
    with open(file_path, 'r') as file:
        for line in file:
            match = import_pattern.match(line)
            if match:
                import_path = match.group(2)
                if match.group(1):
                    import_path = ".".join(import_path.split('.')[:-1])
                imports.append(import_path)
    return imports

# returns full path of all imports in given directory
def get_imports_paths(directory, file_index):
    import_paths = []
    for root, dirs, files in os.walk(directory):
        if 'BUILD.bazel' in files and root != directory:
            continue
        for file in files:
            if file.endswith('.java'):
                file_path = os.path.join(root, file)
                for import_statement in extract_imports(file_path):
                    full_path = file_index.get(import_statement)
                    if full_path:
                        import_paths.append(full_path)
    return import_paths

def main():
    current_dir = os.getcwd()
    workspace_dir = current_dir if 'harness-core' == os.path.basename(current_dir) else None
    if workspace_dir is None:
        raise ValueError("Script is not running in the root of harness-core directory")

    user_dir = input("Enter the directory to scan for Java files: ")

    filter_dir = input("Enter the directory name to filter results: ")
    file_index = index_files_full_address.index_files(workspace_dir)
    
    import_paths = get_imports_paths(user_dir, file_index)
    package_names = get_bazel_directories.get_bazel_directories(import_paths)
    for package_name in sorted(package_names):
        module = os.path.relpath(package_name, workspace_dir)
        if module.startswith(filter_dir):
            print("\"//" + module + ":module\",")

if __name__ == "__main__":
    main()

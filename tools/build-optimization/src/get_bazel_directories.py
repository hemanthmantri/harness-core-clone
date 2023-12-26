# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import os

def find_build_bazel_path(java_file_path):
    """
    Locate the directory containing the BUILD.bazel file
    for the given Java file.
    """
    current_dir = os.path.dirname(java_file_path)
    while current_dir:
        # Check if BUILD.bazel exists in the current directory
        if 'BUILD.bazel' in os.listdir(current_dir):
            return current_dir

        # Move up one directory level
        parent_dir = os.path.dirname(current_dir)
        if parent_dir == current_dir:
            break
        current_dir = parent_dir

    # BUILD.bazel not found, return None
    return None
    
def get_bazel_directories(import_paths):
    """Return list of Bazel directories for given import paths."""

    bazel_directories = set()
    for path in import_paths:
        # Find Bazel directory for the current import path
        bazel_path = find_build_bazel_path(path)
        if bazel_path:
            # Add the Bazel directory to the set if found
            bazel_directories.add(bazel_path)

    return list(bazel_directories)

print(find_build_bazel_path("/Users/pankajkumar/Desktop/harness-core/970-grpc/src/main/java/io/harness/grpc/client/SCMGrpcInterceptor.java"))

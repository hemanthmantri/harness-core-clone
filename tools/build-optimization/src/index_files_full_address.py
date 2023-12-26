# Copyright 2023 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import os

def index_files(root_dir):
    index = {}
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".java"):
                full_path = os.path.join(root, file)
                relative_path = os.path.relpath(full_path, root_dir)
                java_index = relative_path.find('java/')
                if java_index != -1:
                    key = relative_path[java_index + 5:].replace("/", ".").replace("\\", ".")[:-5]
                    index[key] = full_path
    return index

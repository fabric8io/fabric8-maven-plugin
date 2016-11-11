# Changes

This document main purpose is to list changes which might affect backwards compatibility. It will not list all releases as fabric8-maven-plugin.

### 3.1.93

* Changed the base generator configuration `<enabled>` to `<add>` as it means to add this generator's image when it applies in contrast to only run when there is no other image configuration yet.
* Changed the default directories which are picked up the `java-exec` generator to `src/main/fabric8-includes` for extra file to be added to a Docker image. 
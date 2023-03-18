#!/usr/bin/env bash

./fonts.sh

./link_to_mupdf_1.21.1.sh

cd ../

./gradlew clean incVersion

./gradlew assembleFdroidRelease
./gradlew assembleProRelease


./gradlew copyApks -Pbeta
./gradlew -stop

#rm /home/dev/Dropbox/FREE_PDF_APK/testing/*-x86*
#rm /home/dev/Dropbox/FREE_PDF_APK/testing/*-arm*

cd Builder
./remove_all.sh
./install_all.sh
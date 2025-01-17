#!/bin/sh

#This script is intended for launch on *nix machines

#-Xmx4g indicates 4 gb of memory.
#To adjust this (or other Java options), edit the "$HOME/.igv/java_arguments" 
#file.  For more info, see the README at 
#https://raw.githubusercontent.com/igvteam/igv/master/scripts/readme.txt 
#Add the flag -Ddevelopment = true to use features still in development
#Add the flag -Dsun.java2d.uiScale=2 for HiDPI displays
prefix=`dirname $(readlink $0 || echo $0)`

# Check whether or not to use the bundled JDK
if [ -d "${prefix}/jdk-11" ]; then
    echo echo "Using bundled JDK."
    JAVA_HOME="${prefix}/jdk-11"
    PATH=$JAVA_HOME/bin:$PATH
else
    echo "Using system JDK."
fi

<<<<<<< HEAD
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${prefix}
export DYLD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${prefix}

exec java -showversion --module-path="${prefix}/lib" -Xmx4g \
    @"${prefix}/igv.args" \
    -Dsun.java2d.uiScale=2 \
    -Dapple.laf.useScreenMenuBar=true \
    -Djava.net.preferIPv4Stack=true \
	-Djava.library.path=${prefix} \
    --module=org.igv/org.broad.igv.ui.Main "$@"
=======
# Check if there is a user-specified Java arguments file
if [ -e "$HOME/.igv/java_arguments" ]; then
    java -showversion --module-path="${prefix}/lib" -Xmx4g \
        @"${prefix}/igv.args" \
        -Dsun.java2d.uiScale=2 \
        -Dapple.laf.useScreenMenuBar=true \
        -Djava.net.preferIPv4Stack=true \
        @"$HOME/.igv/java_arguments" \
        --module=org.igv/org.broad.igv.ui.Main "$@"
else
    java -showversion --module-path="${prefix}/lib" -Xmx4g \
        @"${prefix}/igv.args" \
        -Dsun.java2d.uiScale=2 \
        -Dapple.laf.useScreenMenuBar=true \
        -Djava.net.preferIPv4Stack=true \
        --module=org.igv/org.broad.igv.ui.Main "$@"
fi
>>>>>>> 0bf49a5b015c03cf2c4a5ab5034f444976c46c8d

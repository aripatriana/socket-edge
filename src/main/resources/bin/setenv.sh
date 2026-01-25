export JAVA_HOME=/usr/lib/jvm/java-21-zulu-openjdk-jdk
export PATH=$JAVA_HOME/bin:$PATH


export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Xms1024m"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Xmx28672m"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Xss228k"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -server"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -XX:+UseZGC -XX:+ZGenerational"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -XX:SoftMaxHeapSize=22937m -XX:+UseLargePages -XX:+AlwaysPreTouch"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -XX:+UnlockExperimentalVMOptions"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Djdk.virtualThreadScheduler.parallelism=128"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Djmx.meter.enabled=true"

export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Dcom.sun.management.jmxremote"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Dcom.sun.management.jmxremote.port=8383"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Dcom.sun.management.jmxremote.rmi.port=8384"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Dcom.sun.management.jmxremote.ssl=false"

export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Djava.rmi.server.hostname=13.131.9.162"
export SOCKET_EDGE_OPTS="$SOCKET_EDGE_OPTS -Djava.net.preferIPv4Stack=true"

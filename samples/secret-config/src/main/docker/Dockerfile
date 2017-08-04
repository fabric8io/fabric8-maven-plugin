FROM frolvlad/alpine-oraclejdk8:slim
VOLUME /tmp
ADD fabric8-maven-sample-secret-config-3.5-SNAPSHOT.jar app.jar
RUN sh -c 'touch /app.jar'
ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]

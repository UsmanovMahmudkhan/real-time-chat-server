FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -DskipTests clean package

FROM tomcat:10.1-jre17-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /workspace/chat-server-war/target/real-time-chat-server.war /usr/local/tomcat/webapps/ROOT.war
RUN chown -R 1000:0 /usr/local/tomcat && chmod -R g=u /usr/local/tomcat
EXPOSE 8080
USER 1000

FROM openjdk:latest
EXPOSE 8082
COPY ./target/moss-1.0-jar-with-dependencies.jar /opt/app/
CMD [ "java","-jar","/opt/app/moss-1.0-jar-with-dependencies.jar", "-c", "/resources/config.yml" ]

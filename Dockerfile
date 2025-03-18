FROM eclipse-temurin:latest
EXPOSE 8080
COPY ./target/moss-1.0-jar-with-dependencies.jar /opt/app/
COPY ./config/moss-default.yml ./config/context.jsonld /config/

CMD [ "java","-jar","/opt/app/moss-1.0-jar-with-dependencies.jar" ]

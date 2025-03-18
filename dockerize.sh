mvn clean package -Dmaven.test.skip
docker build --no-cache -t dbpedia/databus-moss-server:dev .
docker push dbpedia/databus-moss-server:dev

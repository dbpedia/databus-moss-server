mvn package -Dmaven.test.skip
docker build -t dbpedia/databus-moss-server:dev .


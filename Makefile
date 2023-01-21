include deploy/Makefile.local-k8s-cluster.in
include deploy/Makefile.helm-chart.in

.SUFFIXES:
.PHONY: all

remove-all-containers:
	docker system df; \
    docker rm -f $(docker ps -a | grep -v "portainer" | awk 'NR>1 {print $1}'); \
    docker system prune --volumes --force; \
    docker builder prune --all --force; \
    docker buildx prune --all --force --verbose; \
    docker network prune --force; \
    docker volume prune --force; \
    docker system df;

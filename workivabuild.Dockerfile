FROM clojure:lein-2.8.3-alpine as build

# Copy in Source
WORKDIR /build
COPY . /build

# Fetch Dependencies
RUN lein deps

# Lint
RUN lein cljfmt check

# Run Tests
RUN lein test

# Build Docs
RUN lein docs
RUN cd ./documentation && tar cvfz "../utiliva-docs.tgz" ./
ARG BUILD_ARTIFACTS_DOCUMENTATION=/build/utiliva-docs.tgz

# Build Artifact
RUN lein jar
ARG BUILD_ARTIFACTS_JAVA=/build/target/utiliva-*.jar

# Audit Artifacts
RUN lein pom
ARG BUILD_ARTIFACTS_AUDIT=/build/pom.xml

FROM scratch

FROM confluentinc/cp-schema-registry:7.6.1

USER root
COPY aws-msk-iam-auth-2.3.0-all.jar /usr/share/java/schema-registry/aws-msk-iam-auth.jar
COPY aws-msk-iam-auth-2.3.0-all.jar /usr/share/java/cp-base-new/aws-msk-iam-auth.jar
RUN chown appuser:appuser /usr/share/java/schema-registry/aws-msk-iam-auth.jar /usr/share/java/cp-base-new/aws-msk-iam-auth.jar
USER appuser

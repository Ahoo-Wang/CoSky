---
layout: home

hero:
  name: CoSky
  text: Configuration and Service Discovery on Redis
  tagline: For Spring Cloud microservices, with nothing to operate but the Redis you already run
  image:
    src: /cosky-logo.svg
    alt: CoSky
  actions:
    - theme: brand
      text: Get Started
      link: /guide/getting-started
    - theme: alt
      text: Overview
      link: /guide/
    - theme: alt
      text: View on GitHub
      link: https://github.com/Ahoo-Wang/CoSky

features:
  - title: Service Discovery
    icon: 🔍
    details: Applications register themselves, renew, and expire on their own; consumers discover live instances through Spring Cloud, with weighted load balancing and service topology.
  - title: Configuration Management
    icon: ⚙️
    details: Spring properties served from Redis and refreshed live, with versions, rollback, and import/export from Nacos.
  - title: Reads from Memory
    icon: 🚀
    details: Each application keeps a local cache that Redis Pub/Sub keeps current, so reading a config or choosing an instance costs no network round trip.
  - title: Spring Cloud Native
    icon: 🌿
    details: Drop-in Spring Cloud integration via starters for both configuration and service discovery.
  - title: Security & RBAC
    icon: 🔐
    details: JWT authentication, namespace-scoped RBAC, audit logging, and policy-based authorization powered by CoSec.
  - title: Lightweight Deployment
    icon: 📦
    details: Applications need only Redis. The optional REST API server and dashboard run as a Docker image, on Kubernetes, or as a standalone distribution.
---

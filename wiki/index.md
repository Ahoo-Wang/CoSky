---
layout: home

hero:
  name: CoSky
  text: High-Performance Microservice Governance
  tagline: Service Discovery & Configuration backed by Redis — 100K+ QPS, zero extra infrastructure
  image:
    src: /cosky-logo.svg
    alt: CoSky
  actions:
    - theme: brand
      text: Get Started
      link: /guide/getting-started
    - theme: alt
      text: Architecture
      link: /guide/architecture
    - theme: alt
      text: View on GitHub
      link: https://github.com/Ahoo-Wang/CoSky

features:
  - title: Service Discovery
    icon: 🔍
    details: Register and discover services in real-time with Redis-backed registry. Supports weighted load balancing, service topology, and automatic instance renewal.
  - title: Configuration Management
    icon: ⚙️
    details: Dynamic configuration with version control, rollback, and import/export. Changes propagate instantly via Redis PubSub.
  - title: Extreme Performance
    icon: 🚀
    details: 100K+ QPS for standard operations, 70M+ QPS with consistency caching layer. Powered by Redis Lua scripts and in-process caching.
  - title: Spring Cloud Native
    icon: 🌿
    details: Drop-in Spring Cloud integration via starters. Supports both reactive (WebClient) and blocking (RestClient) programming models.
  - title: Security & RBAC
    icon: 🔐
    details: JWT authentication, namespace-scoped RBAC, audit logging, and policy-based authorization powered by CoSec.
  - title: Lightweight Deployment
    icon: 📦
    details: No additional infrastructure required — uses your existing Redis. Deploy as standalone JAR, Docker container, or Kubernetes pod.
---

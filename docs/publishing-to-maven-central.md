---
title: "Three Rejected Uploads: Shipping a Multi-Module Java Project to Maven Central"
date: 2026-06-07
tags: [java, maven, maven-central, multi-module, gpg, devops]
description: "A field report on publishing a six-module Maven reactor to Maven Central — and the three quiet failures that stood between me and a green build."
---

# Three Rejected Uploads: Shipping a Multi-Module Java Project to Maven Central

My build said `BUILD SUCCESS`. Maven Central said *no* — three times.

This is the story of publishing [`real-time-chat-server`](https://central.sonatype.com/namespace/io.github.usmanovmahmudkhan),
a secure, multi-tenant Jakarta WebSocket chat platform, to Maven Central. Not a
single jar — a **six-module Maven reactor** where some modules are libraries
people should depend on, one is a runnable server, and one must *never* leave my
laptop. Every tutorial shows you the happy path. This is the other 20% — the part
that actually ate my afternoon.

If you only want the payoff:

```xml
<dependency>
  <groupId>io.github.usmanovmahmudkhan</groupId>
  <artifactId>chat-server-core</artifactId>
  <version>2.0.0</version>
</dependency>
```

```kotlin
implementation("io.github.usmanovmahmudkhan:chat-server-core:2.0.0")
```

Live now: <https://repo1.maven.org/maven2/io/github/usmanovmahmudkhan/>

---

## The cast: six modules, three destinies

A reactor forces a question a single library never asks: *which of these is the
public artifact?* For me the answer split three ways.

| Module | Coordinates | Destiny |
|---|---|---|
| Parent | `real-time-chat-server-parent` · `pom` | **Publish** — children won't resolve without it |
| Core | `chat-server-core` · `jar` | **Publish** — the library everyone depends on |
| Postgres | `chat-server-postgres` · `jar` | **Publish** — persistence add-on |
| Redis | `chat-server-redis` · `jar` | **Publish** — coordination add-on |
| Server | `real-time-chat-server` · `war` | **Publish** — the runnable app |
| Integration tests | `chat-server-integration-tests` · `jar` | **Never** — a Testcontainers harness |

Five go up. One stays home. Simple — except the tooling disagreed with me about
that last one.

---

## The checklist Central actually enforces

Coming from "just push the jar somewhere," the surprise is how much the registry
*validates* before it accepts a byte. Maven Central (via the Sonatype Central
Portal) demands:

1. A **verified namespace** you provably own.
2. **GPG-signed** artifacts — and your **public key reachable on a keyserver** so
   the signatures can actually be checked.
3. A **sources jar** and a **javadoc jar** beside every main artifact.
4. Full POM metadata: name, description, URL, license, developer, SCM.

Items 1–4 are the well-documented part. I'll cover them fast, then get to the
three failures that aren't in the docs.

### Namespace: the domain-free shortcut

No custom domain? `io.github.<username>` is the fastest namespace to claim. The
portal gives you a random verification key; you create a **public GitHub repo
named exactly that key**, click **Verify**, and delete the throwaway repo once it
flips to **Verified**.

- **groupId:** `io.github.usmanovmahmudkhan`
- **version:** `2.0.0`

### Metadata: write it once, on the parent

In a reactor you don't repeat yourself — `license`, `developers`, `scm`, and
`url` all **inherit** from the parent POM, so the children stay clean:

```xml
<scm>
  <connection>scm:git:https://github.com/usmanovmahmudkhan/real-time-chat-server.git</connection>
  <url>https://github.com/usmanovmahmudkhan/real-time-chat-server</url>
</scm>
```

### Signing lives in a profile

Source and javadoc jars are produced by the main build, so a plain `mvn verify`
is always Central-shaped. The two release-only plugins — GPG signing and the
Central uploader — live behind a `release` profile so CI never needs a private
key just to run tests:

```xml
<profile>
  <id>release</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.sonatype.central</groupId>
        <artifactId>central-publishing-maven-plugin</artifactId>
        <version>0.9.0</version>
        <extensions>true</extensions>
        <configuration>
          <publishingServerId>central</publishingServerId>
          <autoPublish>false</autoPublish>
          <waitUntil>validated</waitUntil>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-gpg-plugin</artifactId>
        <executions>
          <execution>
            <id>sign-artifacts</id>
            <phase>verify</phase>
            <goals><goal>sign</goal></goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```

That `autoPublish=false` / `waitUntil=validated` pairing is a deliberate choice:
the build **uploads and waits for Sonatype to validate**, but stops short of
releasing. The deployment parks in the portal as *validated*, where I can inspect
every file and then pull the trigger myself. On an **immutable** registry, a
human gate before the point of no return is a feature, not friction.

---

## Failure #1 — the module that refused to stay home

I tagged the integration-tests module the "obvious" way:

```xml
<properties>
  <maven.deploy.skip>true</maven.deploy.skip>
</properties>
```

Then I deployed. Central rejected the bundle — with the test module **in it**.

Here's the gotcha: `central-publishing-maven-plugin` doesn't honor per-module
`maven.deploy.skip`. It aggregates the **entire reactor** into one bundle and
uploads the lot. The property quietly does nothing.

The fix that actually works is to remove the module from the reactor at deploy
time:

```bash
mvn -P release clean deploy -pl '!chat-server-integration-tests'
```

> **Lesson:** in a reactor, "don't publish this" is a *reactor* decision
> (`-pl '!module'`), not a module property. Verify what's in the bundle, don't
> assume.

---

## Failure #2 — a valid signature Central couldn't trust

Next upload. New rejection, and a confusing one:

```
Invalid signature for file: chat-server-core-2.0.0.jar.asc
  - Could not find a public key by the key fingerprint.
```

"Invalid signature" — except the signature was *fine*. The real message is the
second line: Central couldn't **find the key** to check it against.

The root cause was lurking in my key's shape. My GPG key is a **primary key plus
a signing subkey**, and gpg signs with the *subkey* by default. So every `.asc`
carried the **subkey's** fingerprint — and `keyserver.ubuntu.com`, the server
Central leans on, returned `404` for that subkey. (Curiously, `keys.openpgp.org`
*could* resolve it, but that wasn't enough on its own.)

Two-part fix. First, force signing with the **primary** key — the trailing `!`
tells gpg "this exact key, no substitutions":

```bash
mvn -P release clean deploy -Dgpg.keyname='84B5635DDAEE196E!'
```

Second, make sure the public key is actually *there*. When `gpg --send-keys`
fails behind a firewall (HKP ports get blocked a lot), POST the armored key
straight to the HTTPS submission endpoint:

```bash
gpg --armor --export 84B5635DDAEE196E | \
  curl --data-urlencode keytext@- https://keyserver.ubuntu.com/pks/add
```

A quick sanity check before re-deploying — this should return `200`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x251106106A95B9EB1D507C9D84B5635DDAEE196E"
```

> **Lesson:** Central verifies the **exact key that signed**. If you have a
> subkey, either publish *that* key where Central looks, or sign with the primary
> via `-Dgpg.keyname=<id>!`.

---

## Failure #3 — the build that wanted Docker

This one failed before it ever reached Central. The reactor's integration tests
use **Testcontainers**, so a plain `mvn deploy` tries to boot real Postgres and
Redis containers. No Docker on the box → the build dies in the `verify` phase,
nowhere near an upload.

Those tests gate the *application*, not the published *libraries*, so for the
release I skipped them and let the unit tests stand guard:

```bash
mvn -P release clean deploy -DskipITs=true
```

> **Lesson:** a release build runs your whole lifecycle. Anything
> environment-dependent — Docker, live services — needs an explicit, intentional
> off switch, not a silent one.

---

## The one command that finally won

Stack all three fixes and the incantation becomes:

```bash
mvn -P release clean deploy \
  -DskipITs=true \
  -Dgpg.keyname='84B5635DDAEE196E!' \
  -pl '!chat-server-integration-tests'
```

This time the bundle sailed through:

```
Deployment 5cb7b864-… has been validated.
To finish publishing visit https://central.sonatype.com/publishing/deployments
```

Five modules, signed, sources + javadoc + SBOM attached, sitting in the portal —
**validated, not yet public.** Exactly the checkpoint I wanted.

---

## Pressing the button

Because I'd chosen `autoPublish=false`, releasing was a separate, deliberate act.
You can click **Publish** in the portal, or do it over the API:

```bash
curl -X POST \
  -H "Authorization: Bearer $(printf '%s:%s' "$TOKEN_USER" "$TOKEN_PASS" | base64)" \
  https://central.sonatype.com/api/v1/publisher/deployment/5cb7b864-…
```

A `204`, the state flipped `VALIDATED → PUBLISHING`, and **about four minutes
later** every artifact answered `200` from the mirror:

```
https://repo1.maven.org/maven2/io/github/usmanovmahmudkhan/chat-server-core/2.0.0/
```

`pom`, `jar`, `-sources.jar`, `-javadoc.jar`, the `.war`, and all the `.asc`
signatures — live. The moment it became real: a stranger on the other side of the
planet can add three lines to a `pom.xml` and pull in code I wrote this morning.

---

## The whole flow, on one page

```
verify namespace ──► fill POM metadata ──► sources + javadoc jars
        │
        ▼
sign with PRIMARY key  ──► publish PUBLIC key to keyserver
        │
        ▼
deploy reactor, minus the test module, ITs skipped
        │
        ▼
Sonatype validates  ──►  [you inspect]  ──►  Publish
        │
        ▼
~4 min later: 200 from repo1.maven.org
```

## Five things I'm taking to the next release

- **The reactor is the hard part.** Decide what's public *per module*, and don't
  trust `maven.deploy.skip` with the Central plugin — exclude with `-pl`.
- **Know which key signs.** Subkeys are invisible to the wrong keyserver; force
  the primary with `-Dgpg.keyname=<id>!` or publish the subkey where Central looks.
- **A release runs everything.** Gate Docker/integration tests with an explicit flag.
- **Stage, then publish.** `autoPublish=false` + `waitUntil=validated` lets you
  eyeball an immutable release before it's carved in stone.
- **Search lag is normal.** Artifacts were resolvable instantly; search.maven.org
  and mvnrepository.com take hours to index. Don't panic when you can't Google it.

Three rejections, one green build, five live artifacts. The code was the easy
part — and the next reactor I publish will be a *lot* less mysterious.

---

**Dependency snippets**

```xml
<dependency>
  <groupId>io.github.usmanovmahmudkhan</groupId>
  <artifactId>chat-server-core</artifactId>
  <version>2.0.0</version>
</dependency>
<dependency>
  <groupId>io.github.usmanovmahmudkhan</groupId>
  <artifactId>chat-server-postgres</artifactId>
  <version>2.0.0</version>
</dependency>
<dependency>
  <groupId>io.github.usmanovmahmudkhan</groupId>
  <artifactId>chat-server-redis</artifactId>
  <version>2.0.0</version>
</dependency>
```

# parqet4j examples

Runnable single-file programs, in the order you would meet them.

| Example          | What it shows                                                                        |
| ---------------- | ------------------------------------------------------------------------------------ |
| `Authorize`      | The OAuth 2.0 authorization-code flow with PKCE, end to end                          |
| `ListPortfolios` | `GET /user`, `GET /portfolios`, holdings, and matching on the sealed asset hierarchy |
| `Activities`     | Streaming every activity of a portfolio, following the cursor lazily                 |
| `Performance`    | KPIs, the per-holding breakdown, and relative intervals                              |
| `SyncActivities` | The write side: creating a holding and booking a batch of activities                 |

`Connect.java` is a shared helper, not an example — it builds a client from `PARQET_ACCESS_TOKEN`.

## Running them

`run.sh` compiles the client once, then uses the JDK's multi-file source launcher.

```bash
# 1. Get a token. Register an integration at https://developer.parqet.com and add
#    http://localhost:1337/callback as a redirect URL, then:
examples/run.sh Authorize <your-client-id>          # read-only
examples/run.sh Authorize <your-client-id> --write  # also request portfolio:write

# 2. Export what it prints.
export PARQET_ACCESS_TOKEN=...

# 3. Explore.
examples/run.sh ListPortfolios
examples/run.sh Activities <portfolio-id>
examples/run.sh Activities <portfolio-id> dividend
examples/run.sh Performance <portfolio-id> --interval 1y
examples/run.sh Performance <portfolio-id> <other-portfolio-id>
```

To see what goes over the wire, put an SLF4J binding on the classpath and enable `DEBUG` for
`dev.ninq.parqet`. Tokens and `Authorization` headers are never logged.

## A warning about `SyncActivities`

It is the only example that writes, and it writes for real: it creates a cash holding and books a
deposit and a buy in whichever portfolio you name. Point it at a portfolio you do not mind touching.
Everything it creates carries a `parqet4j-demo-*` external id, so you can find it again in Parqet.

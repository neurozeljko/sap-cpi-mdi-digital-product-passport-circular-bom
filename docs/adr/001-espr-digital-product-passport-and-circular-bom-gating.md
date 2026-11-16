# ADR-001: EU ESPR 2026/2027 Digital Product Passport & Circular BOM Pre-Ingestion Gate

## Status
Accepted

## Context
EU ESPR (Regulation 2024/1781) and REACH Article 33 require verifiable Digital Product Passports (DPP), GS1 Digital Link serialization, Cradle-to-Gate PCF aggregation, and strict <0.1% w/w SVHC compliance before products enter the EU market or S/4HANA 2025 API_PRODUCT_SRV.

## Decision
Implement a Groovy 3 Reader-streamed validation gate in SAP CPI enforcing GS1 GTIN-14 Mod-10 verification, BigDecimal Circular BOM mass-balance calculations, REACH SVHC screening, and W3C JSON-LD Verifiable Credential minting with SHA-256 provenance hashing.

## Consequences
Non-compliant BOMs are isolated via HTTP 422 quarantine routing before polluting S/4HANA 2025 Material Master tables, while certified products receive tamper-evident W3C DPP credentials.

# -*- coding: utf-8 -*-
"""Shared locations for the JBolt research scripts (no absolute paths in the repository).

PROJECT    = repository root (two levels above this file)
JBOLT_HOME = JBolt source project, default: sibling directory "jbolt_ai_platform_jfinal_customer"
JBOLT_RUN_HOME = JBolt reference run copy, default: sibling directory "jbolt_reference_run"
Override with the environment variables SMA_JBOLT_HOME / SMA_JBOLT_RUN_HOME.
"""
import os
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
WORKSPACE = PROJECT.parent
JBOLT_HOME = Path(os.environ.get("SMA_JBOLT_HOME", WORKSPACE / "jbolt_ai_platform_jfinal_customer"))
JBOLT_RUN_HOME = Path(os.environ.get("SMA_JBOLT_RUN_HOME", WORKSPACE / "jbolt_reference_run"))
RESEARCH_DOCS = PROJECT / "docs" / "jbolt-research"


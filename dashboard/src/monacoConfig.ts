// Only the editor core and the languages a configuration can be written in (see CONFIG_FORMAT_SELECTOR_OPTIONS).
// The full 'monaco-editor' entry also registers the CSS, HTML and TypeScript language services,
// whose workers alone add about 8 MB to the build.
import 'monaco-editor/esm/vs/editor/editor.all.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneGotoLineQuickAccess.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/referenceSearch/standaloneReferenceSearch.js';
import 'monaco-editor/esm/vs/language/json/monaco.contribution.js';
import 'monaco-editor/esm/vs/basic-languages/yaml/yaml.contribution.js';
import 'monaco-editor/esm/vs/basic-languages/xml/xml.contribution.js';
import 'monaco-editor/esm/vs/basic-languages/ini/ini.contribution.js';
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api.js';
import { loader } from '@monaco-editor/react';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';

self.MonacoEnvironment = {
  getWorker(_: unknown, label: string) {
    return label === 'json' ? new jsonWorker() : new editorWorker();
  },
};

loader.config({ monaco });

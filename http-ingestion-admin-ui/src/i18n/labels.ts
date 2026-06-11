import { LocaleMessages } from './types';
import { enumLabel } from './useI18n';

export function modeLabel(messages: LocaleMessages, mode: string | null | undefined) {
  return enumLabel(messages.enum.mode, mode);
}

export function versionStatusLabel(messages: LocaleMessages, status: string | null | undefined) {
  return enumLabel(messages.enum.versionStatus, status);
}

export function jobStatusLabel(messages: LocaleMessages, status: string | null | undefined) {
  return enumLabel(messages.enum.jobStatus, status);
}

export function runTypeLabel(messages: LocaleMessages, runType: string | null | undefined) {
  return enumLabel(messages.enum.runType, runType);
}

export function stageLabel(messages: LocaleMessages, stage: string | null | undefined) {
  return enumLabel(messages.enum.stage, stage);
}

export function httpMethodLabel(messages: LocaleMessages, method: string | null | undefined) {
  return enumLabel(messages.enum.httpMethod, method);
}

export function jobStatusColor(status: string) {
  if (status === 'success') return 'green';
  if (status === 'failed') return 'red';
  if (status === 'running') return 'processing';
  return 'default';
}

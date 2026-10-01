import { requireNativeModule } from "expo";

export type Alarm = {
  id: number;
  hour: number;
  minute: number;
  /** bitmask, bit 0 = Sunday ... bit 6 = Saturday. 0 means ring once. */
  days: number;
  enabled: boolean;
  label: string;
  /** absolute path of the copied audio file, empty = default alarm tone */
  sound: string;
  soundName: string;
};
export type Status = {
  exact: boolean;
  battery: boolean;
  overlay: boolean;
  fullScreen: boolean;
};

const M = requireNativeModule("AlarmNative");

export default {
  getAlarms: async (): Promise<Alarm[]> => JSON.parse(await M.getAlarms()),
  saveAlarms: (a: Alarm[]): Promise<void> => M.saveAlarms(JSON.stringify(a)),
  copyAudio: (uri: string): Promise<string> => M.copyAudio(uri),
  getStatus: (): Promise<Status> => M.getStatus(),
  openSetting: (kind: string): Promise<void> => M.openSetting(kind),
  testRing: (sound: string): Promise<void> => M.testRing(sound),
};

/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.falcosoft.midikeyboard;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.midi.MidiManager;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.mobileer.miditools.MidiConstants;
import com.mobileer.miditools.MidiInputPortSelector;
import com.mobileer.miditools.MusicKeyboardView;

import java.io.IOException;


/**
 * Main activity for the keyboard app.
 */
public class MainActivity extends Activity {
    private static final String TAG = "MidiKeyboard";
    private static final int CONT_ALLNOTESOFF = 123;
    private static final int CONT_MODULATION = 1;
    private static final int CONT_SUSTAIN = 64;

    private MidiInputPortSelector mKeyboardReceiverSelector;
    private MusicKeyboardView mKeyboard;
    private ToggleButton mSustainButton;
    private SeekBar mModualationSeekbar;
    private SeekBar mPitchBendSeekbar;
    private Spinner mProgramSpinner;
    private MidiManager mMidiManager;
    private Toast mToast;
    private Toast mToast2;

    private int mDefaultVelocity = 100;
    private int mOctaveOffset = 0;
    private int mChannel; // ranges from 0 to 15
    private int[] mPrograms = new int[MidiConstants.MAX_CHANNELS]; // ranges from 0 to 127
    private int[] mSustains = new int[MidiConstants.MAX_CHANNELS]; // ranges from 0 to 127
    private int[] mModulations = new int[MidiConstants.MAX_CHANNELS]; // ranges from 0 to 127
    private byte[] mByteBuffer = new byte[3];

    public class ChannelSpinnerActivity implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            ArrayAdapter<CharSequence> adapter;
            if (pos == 9)
                adapter = ArrayAdapter.createFromResource(MainActivity.this,
                        com.falcosoft.midikeyboard.R.array.drumprograms, android.R.layout.simple_spinner_item);
            else
                adapter = ArrayAdapter.createFromResource(MainActivity.this,
                        com.falcosoft.midikeyboard.R.array.programs, android.R.layout.simple_spinner_item);

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mProgramSpinner.setAdapter(adapter);
            adapter.notifyDataSetChanged();

            mChannel = pos & 0x0F;
            updateProgramText();
            mSustainButton.setChecked(mSustains[mChannel] == 127);
            midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_SUSTAIN, mSustains[mChannel]);
            mModualationSeekbar.setProgress(mModulations[mChannel]);
            midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_MODULATION, mModulations[mChannel]);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    public class ProgramSpinnerActivity implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            setProgram(pos);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt("mOctaveOffset", mOctaveOffset);
        savedInstanceState.putInt("mChannel", mChannel);

        for (int i = 0; i < MidiConstants.MAX_CHANNELS; i++) {
            savedInstanceState.putInt("mSustain" + Integer.toString(i), mSustains[i]);
            savedInstanceState.putInt("mProgram" + Integer.toString(i), mPrograms[i]);
            savedInstanceState.putInt("mModulation" + Integer.toString(i), mModulations[i]);
        }

    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mOctaveOffset = savedInstanceState.getInt("mOctaveOffset");
        mChannel = savedInstanceState.getInt("mChannel");

        for (int i = 0; i < MidiConstants.MAX_CHANNELS; i++) {
            mPrograms[i] = savedInstanceState.getInt("mProgram" + Integer.toString(i));
            mSustains[i] = savedInstanceState.getInt("mSustain" + Integer.toString(i));
            mModulations[i] = savedInstanceState.getInt("mModulation" + Integer.toString(i));
        }

        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        mSustainButton.setChecked(mSustains[mChannel] == 127);
                        midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_SUSTAIN, mSustains[mChannel]);
                        mProgramSpinner.setSelection(mPrograms[mChannel]);
                        setProgram(mPrograms[mChannel]);
                        mModualationSeekbar.setProgress(mModulations[mChannel]);
                        midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_MODULATION, mModulations[mChannel]);
                    }
                },
                500);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(com.falcosoft.midikeyboard.R.layout.activity_main);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            setupMidi();
        } else {
            Toast.makeText(this, "MIDI not supported!", Toast.LENGTH_LONG)
                    .show();
        }

        mToast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);
        mToast2 = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);

        Spinner spinner = (Spinner) findViewById(com.falcosoft.midikeyboard.R.id.spinner_channels);
        spinner.setOnItemSelectedListener(new ChannelSpinnerActivity());

        mProgramSpinner = (Spinner) findViewById(com.falcosoft.midikeyboard.R.id.spinner_programs);
        mProgramSpinner.setOnItemSelectedListener(new ProgramSpinnerActivity());

        mSustainButton = (ToggleButton) findViewById(com.falcosoft.midikeyboard.R.id.button_sustain);
        mSustainButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mSustains[mChannel] = 127;
                    midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_SUSTAIN, 127);
                } else {
                    mSustains[mChannel] = 0;
                    midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_SUSTAIN, 0);
                }
            }
        });

        SeekBar simpleSeekBar = (SeekBar) findViewById(com.falcosoft.midikeyboard.R.id.seekBar_velocity);
        // perform seek bar change listener event used for getting the progress value
        simpleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mDefaultVelocity = progress;
                mToast.setText("Velocity: " + progress);
                mToast.show();
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                mToast.setText("Velocity: " + mDefaultVelocity);
                mToast.show();
            }

            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mModualationSeekbar = (SeekBar) findViewById(com.falcosoft.midikeyboard.R.id.seekBar_modulation);
        // perform seek bar change listener event used for getting the progress value
        mModualationSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mModulations[mChannel] = progress;
                midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_MODULATION, mModulations[mChannel]);
                mToast2.setText("Channel " + Integer.toString(mChannel + 1) + " Modulation: " + progress);
                mToast2.show();
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                mToast2.setText("Channel " + Integer.toString(mChannel + 1) + " Modulation: " + mModulations[mChannel]);
                mToast2.show();
            }

            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mPitchBendSeekbar = (SeekBar) findViewById(com.falcosoft.midikeyboard.R.id.seekBar_pitchBend);
        // perform seek bar change listener event used for getting the progress value
        mPitchBendSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                midiCommand(MidiConstants.STATUS_PITCH_BEND + mChannel, progress & 0x7F, progress >> 7);

            }

            public void onStartTrackingTouch(SeekBar seekBar) {

                int progress = seekBar.getProgress();
                midiCommand(MidiConstants.STATUS_PITCH_BEND + mChannel, progress & 0x7F, progress >> 7);

            }

            public void onStopTrackingTouch(SeekBar seekBar) {

                mPitchBendSeekbar.setProgress(8192);
                int progress = 8192;
                midiCommand(MidiConstants.STATUS_PITCH_BEND + mChannel, progress & 0x7F, progress >> 7);

            }
        });

    }

    private void setupMidi() {
        mMidiManager = (MidiManager) getSystemService(MIDI_SERVICE);
        if (mMidiManager == null) {
            Toast.makeText(this, "MidiManager is null!", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        // Setup Spinner that selects a MIDI input port.
        mKeyboardReceiverSelector = new MidiInputPortSelector(mMidiManager,
                this, com.falcosoft.midikeyboard.R.id.spinner_receivers);

        mKeyboard = (MusicKeyboardView) findViewById(com.falcosoft.midikeyboard.R.id.musicKeyboardView);
        mKeyboard.addMusicKeyListener(new MusicKeyboardView.MusicKeyListener() {
            @Override
            public void onKeyDown(int keyIndex) {
                noteOn(mChannel, keyIndex, mDefaultVelocity);
            }

            @Override
            public void onKeyUp(int keyIndex) {
                noteOff(mChannel, keyIndex, mDefaultVelocity);
            }
        });

    }


    public void onOctaveDown(View view) {
        midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_ALLNOTESOFF, 1); //All Notes Off
        if (mOctaveOffset > -6) mOctaveOffset--;
        mToast.setText("Octave offset: " + mOctaveOffset);
        mToast.show();
    }

    public void onOctaveUp(View view) {
        midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_ALLNOTESOFF, 1); //All Notes Off
        if (mOctaveOffset < 6) mOctaveOffset++;
        mToast.setText("Octave offset: " + mOctaveOffset);
        mToast.show();
    }


    private void setProgram(int value) {
        midiCommand(MidiConstants.STATUS_PROGRAM_CHANGE + mChannel, value);
        mPrograms[mChannel] = value;
        //  updateProgramText();
    }

    private void updateProgramText() {
        // if(mProgramSpinner.getSelectedItemPosition() != mPrograms[mChannel])
        {
            mProgramSpinner.setSelection(mPrograms[mChannel]);
        }
    }

    private void noteOff(int channel, int pitch, int velocity) {
        pitch = pitch + (mOctaveOffset * 12);
        midiCommand(MidiConstants.STATUS_NOTE_OFF + channel, pitch, velocity);
    }

    private void noteOn(int channel, int pitch, int velocity) {
        pitch = pitch + (mOctaveOffset * 12);
        midiCommand(MidiConstants.STATUS_NOTE_ON + channel, pitch, velocity);
    }

    private void midiCommand(int status, int data1, int data2) {
        mByteBuffer[0] = (byte) status;
        mByteBuffer[1] = (byte) data1;
        mByteBuffer[2] = (byte) data2;
        long now = System.nanoTime();
        midiSend(mByteBuffer, 3, now);
    }

    private void midiCommand(int status, int data1) {
        mByteBuffer[0] = (byte) status;
        mByteBuffer[1] = (byte) data1;
        long now = System.nanoTime();
        midiSend(mByteBuffer, 2, now);
    }

    private void closeSynthResources() {
        if (mKeyboardReceiverSelector != null) {
            mKeyboardReceiverSelector.close();
            mKeyboardReceiverSelector.onDestroy();
        }
    }

    @Override
    public void onDestroy() {
        midiCommand(MidiConstants.STATUS_CONTROL_CHANGE + mChannel, CONT_ALLNOTESOFF, 1); //All Notes Off
        closeSynthResources();
        super.onDestroy();
    }

    private void midiSend(byte[] buffer, int count, long timestamp) {
        if (mKeyboardReceiverSelector != null) {
            try {
                // send event immediately
                MidiReceiver receiver = mKeyboardReceiverSelector.getReceiver();
                if (receiver != null) {
                    receiver.send(buffer, 0, count, timestamp);
                }
            } catch (IOException e) {
                Log.e(TAG, "mKeyboardReceiverSelector.send() failed " + e);
            }
        }
    }
}

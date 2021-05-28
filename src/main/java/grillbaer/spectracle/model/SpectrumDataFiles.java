package grillbaer.spectracle.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import grillbaer.spectracle.spectrum.SampleLine;
import grillbaer.spectracle.spectrum.Spectrum;
import grillbaer.spectracle.spectrum.WaveLengthCalibration;
import grillbaer.spectracle.spectrum.WaveLengthCalibration.WaveLengthPoint;
import lombok.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class SpectrumDataFiles {
    public void writeCsvFile(@NonNull Spectrum rawSpectrum, Spectrum processedSpectrum,
                             @NonNull Path targetFile) throws IOException {

        if (processedSpectrum != null && rawSpectrum.getLength() != processedSpectrum.getLength())
            throw new IllegalArgumentException("raw and processed spectrum must have equal length but have"
                    + rawSpectrum.getLength() + " <> " + processedSpectrum.getLength());

        final var writer = new CsvMapper()
                .writerFor(DataPoint.class).with(createCsvSpectrumSchema());

        try (var sequenceWriter = writer.writeValues(targetFile.toFile())) {
            for (int i = 0; i < rawSpectrum.getLength(); i++) {
                sequenceWriter.write(
                        new DataPoint(i,
                                rawSpectrum.getNanoMetersAtIndex(i),
                                rawSpectrum.getValueAtIndex(i),
                                processedSpectrum != null ? processedSpectrum.getValueAtIndex(i) : null));
            }
        }
    }

    public Spectra readCsvFile(@NonNull Path sourceFile) throws IOException {
        final var reader = new CsvMapper()
                .readerFor(DataPoint.class).with(createCsvSpectrumSchema());
        try (var sequenceReader = reader.<DataPoint>readValues(sourceFile.toFile())) {
            final var dataPoints = sequenceReader.readAll();
            if (dataPoints.size() < 2)
                throw new IOException("File " + sourceFile + " must contain at least two data points");

            final var calibration = WaveLengthCalibration.create(
                    new WaveLengthPoint(0., dataPoints.get(0).getWaveLength()),
                    new WaveLengthPoint(1., dataPoints.get(dataPoints.size() - 1).getWaveLength()));

            final var raw = dataPoints.stream().mapToDouble(DataPoint::getIntensityRaw).toArray();
            final var processed = dataPoints.stream()
                    .map(DataPoint::getIntensityProcessed).filter(Objects::nonNull)
                    .mapToDouble(v -> v).toArray();

            return new Spectra(Spectrum.create(SampleLine.create(raw), calibration),
                    processed.length == raw.length ? Spectrum.create(SampleLine.create(processed), calibration) : null);
        }
    }

    CsvSchema createCsvSpectrumSchema() {
        return new CsvSchema.Builder()
                .addColumn("index", CsvSchema.ColumnType.NUMBER)
                .addColumn("wavelength", CsvSchema.ColumnType.NUMBER)
                .addColumn("intensityRaw", CsvSchema.ColumnType.NUMBER)
                .addColumn("intensityProcessed", CsvSchema.ColumnType.NUMBER)
                .setUseHeader(true)
                .setAllowComments(true)
                .build();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    private static class DataPoint {
        @JsonProperty("index")
        private int index;
        @JsonProperty("wavelength")
        private double waveLength;
        @JsonProperty("intensityRaw")
        private double intensityRaw;
        @JsonProperty("intensityProcessed")
        private Double intensityProcessed;
    }

    @AllArgsConstructor
    @Getter
    public static class Spectra {
        @NonNull
        private final Spectrum raw;
        private final Spectrum processed;
    }
}

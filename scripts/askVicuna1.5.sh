cd ..

GPU=$1

python askHF.py \
--ckpt_path lmsys/vicuna-7b-v1.5 \
--tokenizer_path lmsys/vicuna-7b-v1.5 \
--result_dir ./results/vicuna1.5/ \
--shot_number 1 \
--gpu $GPU

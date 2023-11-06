cd ..

python eval/evaluator.py \
--answer_dir=./results/vicuna1.5/one_shot/ \
--pattern_path=./eval/pat_list.txt \
--dataset_path=./dataset/question.jsonl \
--model=vicuna \
--passk=1
